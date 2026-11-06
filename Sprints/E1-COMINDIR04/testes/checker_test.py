"""Testa os cálculos com valores conhecidos, separados dos ensaios medidos."""
from pathlib import Path
import datetime
import json
import subprocess
import tempfile
import ensaios as e

folder = Path(tempfile.mkdtemp(prefix="rep04-checker-"))
def write(name, value):
    (folder / name).write_text(value, encoding="utf-8")

base = int(datetime.datetime(2026, 11, 3, 10, 0).timestamp() * 1000)
def event(offset, rest):
    return str(base + offset) + ";" + rest + "\n"

# Um lote com dois registos, um vazio, e um lote de outra incarnação.
write("lider-p0.txt",
      "1;S01;20.0;2026-11-03T10:00:00;1;1\n"
      "2;S01;21.0;2026-11-03T10:00:01;2;2\n"
      "3;S01;22.0;2026-11-03T10:00:30;3;3\n")
write("hb-p0.txt", f"{base};1;{base+2000};2;2;200\n"
      f"{base};2;{base+7000};2;0;50\n"
      f"{base+30000};1;{base+31000};3;1;100\n")
write("monitor-replica1.txt",
      event(2100, f"RECEBIDO;p0;inc={base};hb=1;ultimoSeq=2;n=2;de=1;ate=2")
      + event(2200, f"RECEBIDO;p0;inc={base};hb=1;ultimoSeq=2;n=2;de=1;ate=2")
      + event(6000, "SUSPEITA;p0;silencio_ms=3900")
      + event(7100, f"RETOMA;p0;inc={base};hb=2")
      + event(7100, f"RECEBIDO;p0;inc={base};hb=2;ultimoSeq=2;n=0;de=0;ate=0")
      + event(22000, "SUSPEITA;p0;silencio_ms=14900")
      + event(31000, f"REINICIO;p0;inc={base}->{base+30000}")
      + event(31100, f"RETOMA;p0;inc={base+30000};hb=1")
      + event(31100, f"RECEBIDO;p0;inc={base+30000};hb=1;ultimoSeq=3;n=1;de=3;ate=3")
      + event(31200, "HB_PERDIDO;p0;esperado=2;recebido=3")
      + event(31300, "RECUPERACAO;p0;de=1;ate=2"))
write("falhas.txt", f"p0;{base+8000}\n")
cmd = [e.JAVA, "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp",
       str(e.ROOT / "rep04-replica/out"), "rep04.HeartbeatCheck", "1", str(folder), str(folder / "falhas.txt")]
result = subprocess.run(cmd, capture_output=True, creationflags=e.FLAGS, check=True).stdout.decode("utf-8").replace("\r\n", "\n")
assert "falhas_reais=1;falsas_suspeitas=1" in result, result
assert "detecao_ms=14000" in result, result
assert "LATENCIA;p0;amostras=3;media_ms=1433.333;max_ms=2100.000" in result, result
assert "LOTE_total_por_registo=116.667" in result, result
assert "HB_PERDIDO=1;RECUPERACAO=1" in result, result
assert "intervalo_media_ms=5000.000" in result, result
out = e.RESULTS / "testes-dirigidos"
out.mkdir(parents=True, exist_ok=True)
(out / "checker-sintetico.txt").write_text("FIXTURE SINTETICA; nao sao medicoes do protocolo\n" + result, encoding="utf-8")
print("CheckerTest PASS: duas incarnacoes, falso/real, deduplicacao de amostras, bytes e detecao.")
