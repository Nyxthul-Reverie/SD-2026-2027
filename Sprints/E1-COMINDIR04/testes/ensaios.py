"""Ensaios locais COMINDIR04. Python só automatiza processos; o protocolo é Java/Kubo."""
import argparse
import ctypes
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get("REP04_JDK", str(Path.home() / ".jdks/openjdk-23")))
JAVA = str(JDK / "bin/java.exe")
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
RESULTS = ROOT / "resultados"
PROCESSES = []
SUSPENDED = []
KUBO = None
API_PORT = 5003
API = "http://127.0.0.1:5003/api/v0"

def now():
    return int(time.time() * 1000)

def read(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""

def lines(path):
    return [x for x in read(path).splitlines() if x.strip()]

def wait_for(predicate, timeout=25, reason="condicao"):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(.05)
    raise AssertionError("Timeout: " + reason)

def pause(seconds):
    time.sleep(max(0, seconds))

def kubo():
    global KUBO
    # Rede isolada: sem bootstrap, sem descoberta LAN e swarm apenas em loopback.
    # Evita misturar estes tópicos com os de outros alunos na rede pública.
    repo = ROOT / "ensaios/kubo-isolado"
    env = os.environ.copy()
    env["IPFS_PATH"] = str(repo)
    binary = shutil.which("ipfs")
    if not (repo / "config").exists():
        repo.mkdir(parents=True, exist_ok=True)
        subprocess.run([binary, "init"], env=env, check=True, capture_output=True, creationflags=FLAGS)
        for args in [
            ["bootstrap", "rm", "--all"],
            ["config", "--json", "Discovery.MDNS.Enabled", "false"],
            ["config", "--json", "Pubsub.Enabled", "true"],
            ["config", "--json", "Addresses.Swarm", '["/ip4/127.0.0.1/tcp/4003"]'],
            ["config", "Addresses.API", "/ip4/127.0.0.1/tcp/5003"],
            ["config", "Addresses.Gateway", "/ip4/127.0.0.1/tcp/8083"],
        ]:
            subprocess.run([binary] + args, env=env, check=True, capture_output=True, creationflags=FLAGS)
    try:
        urllib.request.urlopen(urllib.request.Request(API + "/id", method="POST"), timeout=2)
        return
    except Exception:
        pass
    RESULTS.mkdir(exist_ok=True)
    output = open(RESULTS / "kubo-console.txt", "ab")
    KUBO = subprocess.Popen([binary, "daemon"], env=env, stdout=output, stderr=subprocess.STDOUT,
                            creationflags=FLAGS)
    output.close()
    def ready():
        try:
            urllib.request.urlopen(urllib.request.Request(API + "/id", method="POST"), timeout=1)
            return True
        except Exception:
            return False
    wait_for(ready, 30, "Kubo")

class Run:
    def __init__(self, name, period=5000, factor=3, mutation=None):
        self.path = RESULTS / name
        self.path.mkdir(parents=True, exist_ok=False)
        self.period = period
        self.factor = factor
        self.mutation = mutation
        self.processes = {}
        self.leader_root = ROOT / "rep04-lider/out"
        self.meta = {"name": name, "periodo_ms": period, "fator": factor, "intervalo_rajada_ms": 10,
                     "inicio": now(), "mutacao": mutation, "estado": "RUNNING", "api": API,
                     "rede": "Kubo isolado: sem bootstrap/MDNS, swarm em loopback"}
        self.save()
        if mutation:
            self.leader_root = self.make_mutation(mutation)

    def save(self):
        (self.path / "ensaio.json").write_text(json.dumps(self.meta, ensure_ascii=False, indent=2), encoding="utf-8")

    def make_mutation(self, kind):
        # Cópias isoladas: o código correto nunca é substituído.
        dest = ROOT / "ensaios" / ("mutacao-" + kind)
        source = dest / "src"
        source.mkdir(parents=True, exist_ok=True)
        for file in (ROOT / "rep04-lider/src/rep04").glob("*.java"):
            shutil.copyfile(file, source / file.name)
        file = source / "PartitionLeader.java"
        text = file.read_text(encoding="utf-8")
        if kind == "sem-vazios":
            text = text.replace("List<SensorRecord> fechados = pendentes;",
                                "if (pendentes.isEmpty()) return;\n            List<SensorRecord> fechados = pendentes;")
        elif kind == "relogios":
            text = text.replace("List<SensorRecord> fechados = pendentes;",
                                "lamport++;\n            vector.tick(k);\n            List<SensorRecord> fechados = pendentes;")
        elif kind == "fora-lock":
            old = """Batch lote;
        synchronized (file) {
            List<SensorRecord> fechados = pendentes;
            pendentes = new ArrayList<>();
            lote = new Batch(endereco, porto, inc, ++hb, lastSeq, fechados);
        }"""
            new = """Batch lote;
        List<SensorRecord> fechados;
        long numero;
        synchronized (file) {
            fechados = pendentes;
            pendentes = new ArrayList<>();
            numero = ++hb;
        }
        // Janela de teste: torna observável a corrida da leitura fora do lock.
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        lote = new Batch(endereco, porto, inc, numero, lastSeq, fechados);"""
            assert old in text
            text = text.replace(old, new)
        elif kind == "ultimo-zero":
            text = text.replace("inc, ++hb, lastSeq, fechados", "inc, ++hb, 0, fechados")
            text = text.replace("// HTTP pode demorar.", "if (lote.hb % 10 == 0) return;\n        // HTTP pode demorar.")
        file.write_text(text, encoding="utf-8")
        out = dest / "out"
        out.mkdir(exist_ok=True)
        subprocess.run([str(JDK / "bin/javac.exe"), "--release", "11", "-encoding", "UTF-8", "-d", str(out)]
                       + [str(p) for p in source.glob("*.java")], check=True, creationflags=FLAGS)
        (self.path / "mutacao-PartitionLeader.java.txt").write_text(text, encoding="utf-8")
        return out

    def start(self, role, cls, args, name):
        if role == "lider":
            args = args + ["127.0.0.1", API]
        elif role == "replica":
            args = args + [str(API_PORT)]
        else:
            args = args + [API]
        out = self.leader_root if role == "lider" else ROOT / ("rep04-" + role) / "out"
        log = open(self.path / (name + "-console.txt"), "ab")
        # Nos testes, o fator alterado só se aplica aos participantes.
        factor = self.factor if role != "lider" else 3
        proc = subprocess.Popen([JAVA, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                                 "-Dstderr.encoding=UTF-8", "-Drep04.periodo=" + str(self.period),
                                 "-Drep04.fator=" + str(factor), "-Drep04.intervalo=10",
                                 "-cp", str(out), "rep04." + cls] + args,
                                cwd=self.path, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                creationflags=FLAGS)
        log.close()
        self.processes[name] = proc
        PROCESSES.append(proc)
        return proc

    def boot(self):
        for r, parts in [(1, "0,1"), (2, "1,2"), (3, "0,2")]:
            self.start("replica", "Replica", [str(r), parts], "replica" + str(r))
        pause(1.5)
        for k in range(3):
            self.leader(k)
        pause(1)
        self.start("processador", "AlarmProcessor", ["3"], "processador")
        pause(.5)
        self.start("produtor", "Producer", ["3"], "produtor")
        wait_for(lambda: "Produtor pronto" in read(self.path / "produtor-console.txt"), reason="produtor pronto")
        if self.mutation != "sem-vazios":
            wait_for(lambda: all("RECEBIDO;p" + str(k) in read(self.path / "monitor-produtor.txt") for k in range(3)),
                     self.period / 1000 + 10, "primeiros heartbeats")
        else:
            pause(1)
        self.meta["pronto"] = now()
        self.save()

    def leader(self, k):
        self.start("lider", "PartitionLeader", [str(k), "3"], "lider" + str(k))
        wait_for(lambda: self.processes["lider" + str(k)].poll() is None and
                 "pronto" in read(self.path / ("lider" + str(k) + "-console.txt")), reason="lider pronto")

    def send(self, command):
        proc = self.processes["produtor"]
        proc.stdin.write((command + "\n").encode("utf-8"))
        proc.stdin.flush()

    def records(self, k, replica=None):
        name = "lider-p" + str(k) if replica is None else "replica-" + str(replica) + "-p" + str(k)
        return lines(self.path / (name + ".txt"))

    def hb(self, k):
        return [list(map(int, x.split(";"))) for x in lines(self.path / ("hb-p" + str(k) + ".txt"))]

    def kill(self, k):
        stamp = now()
        with open(self.path / "falhas.txt", "a", encoding="utf-8") as f:
            f.write("p" + str(k) + ";" + str(stamp) + "\n")
        self.processes["lider" + str(k)].kill()
        self.processes["lider" + str(k)].wait(5)
        return stamp

    def stop(self):
        for proc in self.processes.values():
            if proc.poll() is None:
                proc.kill()
        for proc in self.processes.values():
            proc.wait(10)
            if proc.stdin:
                proc.stdin.close()
        pause(.5)

    def check(self):
        for cls in ["HeartbeatCheck", "CausalCheck"]:
            cmd = [JAVA, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                   "-cp", str(ROOT / "rep04-replica/out"),
                   "rep04." + cls, "3", str(self.path)]
            if cls == "HeartbeatCheck" and (self.path / "falhas.txt").exists():
                cmd.append(str(self.path / "falhas.txt"))
            result = subprocess.run(cmd, capture_output=True, creationflags=FLAGS)
            (self.path / (cls + ".txt")).write_bytes(result.stdout + result.stderr)
            if result.returncode:
                raise AssertionError(cls + " falhou: " + result.stderr.decode("utf-8", errors="replace"))
        causal = read(self.path / "CausalCheck.txt")
        assert "Violações V[k] == seq: 0" in causal and "Violações da condição de Lamport: 0" in causal, causal

    def finish(self, verify_replicas=True, expected_warning=None):
        self.stop()
        self.check()
        if expected_warning:
            assert expected_warning in read(self.path / "HeartbeatCheck.txt"), "Efeito da mutação não observado"
        agreement = {}
        for r, parts in [(1, [0, 1]), (2, [1, 2]), (3, [0, 2])]:
            for k in parts:
                agreement[f"replica{r}-p{k}"] = self.records(k, r) == self.records(k)
        self.meta["replicas_iguais_ao_lider"] = agreement
        if verify_replicas:
            assert all(agreement.values()), agreement
        self.meta.update(estado="PASS", fim=now())
        self.save()
        print("PASS", self.path.name, flush=True)

def suspend(proc, resume=False):
    if os.name == "nt":
        fn = ctypes.windll.ntdll.NtResumeProcess if resume else ctypes.windll.ntdll.NtSuspendProcess
        fn.argtypes = [ctypes.c_void_p]
        fn.restype = ctypes.c_long
        status = fn(ctypes.c_void_p(int(proc._handle)))
        if status != 0:
            raise OSError("NtSuspend/ResumeProcess: " + str(status))
    else:
        import signal
        os.kill(proc.pid, signal.SIGCONT if resume else signal.SIGSTOP)
    if resume:
        SUSPENDED.remove(proc)
    else:
        SUSPENDED.append(proc)

def run_a():
    r = Run("8a-falha-ultimo")
    r.boot()
    r.send("S01 21.0")
    pause(2)
    r.send("S04 22.5")
    wait_for(lambda: len(r.records(0)) == 2)
    wait_for(lambda: len(r.hb(0)) >= 2 and r.hb(0)[-1][3:5] == [2, 0], 15, "lote vazio apos dados")
    r.send("S01 21.4")
    wait_for(lambda: len(r.records(0)) == 3, 1, "append antes da falha")
    r.kill(0)
    assert len(r.records(0, 1)) == 2 and len(r.records(0, 3)) == 2
    wait_for(lambda: all("SUSPEITA;p0" in read(r.path / ("monitor-" + name + ".txt"))
                        for name in ["replica1", "replica3", "produtor", "processador"]), 20, "suspeita p0")
    r.leader(0)
    wait_for(lambda: len(r.records(0, 1)) == 3 and len(r.records(0, 3)) == 3, 10, "recuperacao final")
    for name in ["replica1", "replica3"]:
        text = read(r.path / ("monitor-" + name + ".txt"))
        assert "REINICIO;p0" in text and "RECUPERACAO;p0;de=3;ate=3" in text
    r.finish()

def run_b(period):
    r = Run("8b-rajada-" + str(period), period)
    r.boot()
    r.send("rajada 300 10")
    wait_for(lambda: "Rajada concluída" in read(r.path / "produtor-console.txt"), 60, "rajada")
    pause(2 * period / 1000 + 2)
    r.finish()
    r = Run("8b-parado-" + str(period), period)
    r.boot()
    r.meta["janela_sem_leituras_inicio"] = now()
    pause(60)
    r.meta["janela_sem_leituras_fim"] = now()
    r.finish()

def run_c(period, repetition):
    r = Run("8c-falha-" + str(period) + "-" + str(repetition), period)
    r.boot()
    r.send("rajada 300 10")
    wait_for(lambda: read(r.path / "produtor-console.txt").count("Publicado em") >= 30, 15, "rajada em curso")
    assert "Rajada concluída" not in read(r.path / "produtor-console.txt")
    r.kill(1)
    pause(30)
    r.leader(1)
    pause(2 * period / 1000 + 2)
    r.finish()

def run_d():
    r = Run("8d-suspensao")
    r.boot()
    proc = r.processes["lider2"]
    r.meta["suspensao"] = now()
    suspend(proc)
    r.send("S03 24.0")
    pause(10)
    r.send("S06 25.0")
    pause(10)
    suspend(proc, True)
    r.meta["retoma"] = now()
    pause(12)
    for name in ["replica2", "replica3", "produtor", "processador"]:
        text = read(r.path / ("monitor-" + name + ".txt"))
        assert "SUSPEITA;p2" in text and "RETOMA;p2" in text
        assert "REINICIO;p2" not in text
    r.finish()

def run_fault(kind):
    r = Run("9-" + kind, factor=1 if kind == "fator1" else 3,
            mutation=None if kind == "fator1" else kind)
    r.boot()
    if kind == "sem-vazios":
        r.send("S01 21.0")
        r.send("S02 22.0")
        r.send("S03 23.0")
        wait_for(lambda: all(r.hb(k) for k in range(3)), 10)
        pause(18)
        assert "SUSPEITA;p0" in read(r.path / "monitor-replica1.txt")
        r.finish()
    elif kind == "relogios":
        r.send("S01 21.0")
        pause(3)
        assert "diferente de seq" in read(r.path / "lider0-console.txt")
        r.finish()
    elif kind == "fora-lock":
        r.send("rajada 300 10")
        wait_for(lambda: "Rajada concluída" in read(r.path / "produtor-console.txt"), 60)
        pause(12)
        r.finish(expected_warning="AVISO_CABECALHO")
    elif kind == "ultimo-zero":
        wait_for(lambda: all(r.hb(k) and r.hb(k)[-1][1] >= 9 for k in range(3)), 50)
        r.send("S01 21.0")
        r.send("S02 22.0")
        r.send("S03 23.0")
        wait_for(lambda: all(len(r.records(k)) == 1 for k in range(3)), 2)
        pause(12)
        assert len(r.records(0, 1)) == 0
        r.finish(False)
    else:
        r.send("rajada 300 10")
        wait_for(lambda: "Rajada concluída" in read(r.path / "produtor-console.txt"), 60)
        pause(15)
        r.finish()

def main():
    global RESULTS
    parser = argparse.ArgumentParser()
    parser.add_argument("suite", choices=["a", "normal", "falhas", "all"])
    parser.add_argument("--saida", type=Path, help="Pasta nova para os resultados; evita misturar ensaios")
    args = parser.parse_args()
    if args.saida:
        RESULTS = args.saida.resolve()
    kubo()
    try:
        if args.suite in ["a", "all"]:
            run_a()
        if args.suite in ["normal", "all"]:
            for period in [5000, 1000, 10000]:
                run_b(period)
                for repetition in range(1, 4 if period == 5000 else 2):
                    run_c(period, repetition)
            run_d()
        if args.suite in ["falhas", "all"]:
            for kind in ["sem-vazios", "relogios", "fora-lock", "ultimo-zero", "fator1"]:
                run_fault(kind)
    finally:
        for proc in list(SUSPENDED):
            if proc.poll() is None:
                suspend(proc, True)
        for proc in PROCESSES:
            if proc.poll() is None:
                proc.kill()
                proc.wait(5)
        if KUBO and KUBO.poll() is None:
            KUBO.terminate()
            KUBO.wait(10)

if __name__ == "__main__":
    main()
