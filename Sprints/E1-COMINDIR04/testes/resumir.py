"""Resume os logs guardados; não gera medições novas nem substitui os verificadores."""
import json
from pathlib import Path
import statistics
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "resultados"
def read(path):
    return path.read_text(encoding="utf-8")
def mean(values):
    return statistics.mean(values) if values else None

def fields(line):
    result = {}
    for value in line.split(";"):
        if "=" in value:
            key, data = value.split("=", 1)
            result[key] = data
    return result

summaries = {}
for folder in sorted(root.iterdir()):
    if not folder.is_dir() or not (folder / "ensaio.json").exists():
        continue
    meta = json.loads(read(folder / "ensaio.json"))
    if meta["estado"] != "PASS":
        continue
    summary = {"configuracao": meta}
    sizes, intervals, bytes_all, bytes_data = [], [], 0, 0
    counts, window_counts, accepted = [], [], []
    for k in range(3):
        rows = [list(map(int, line.split(";"))) for line in read(folder / f"hb-p{k}.txt").splitlines()] if (folder / f"hb-p{k}.txt").exists() else []
        counts.append(len(rows))
        previous = {}
        for inc, hb, instant, last, count, size in rows:
            sizes.append(count)
            bytes_all += size
            if count:
                bytes_data += size
            if inc in previous:
                intervals.append(instant - previous[inc])
            previous[inc] = instant
        if "janela_sem_leituras_inicio" in meta:
            begin, end = meta["janela_sem_leituras_inicio"], meta["janela_sem_leituras_fim"]
            window_counts.append(sum(begin <= row[2] < end for row in rows) * 60000 / (end - begin))
        lines = read(folder / f"lider-p{k}.txt").splitlines()
        accepted.append({"normais": sum(not line.split(";")[1].startswith("ALARM-") for line in lines),
                         "alarmes": sum(line.split(";")[1].startswith("ALARM-") for line in lines)})
    summary.update(lotes_por_particao=counts, registos_transportados=sum(sizes),
                   registos_por_lote_media=mean(sizes), registos_por_lote_max=max(sizes) if sizes else None,
                   vazios_pct=100 * sizes.count(0)/len(sizes) if sizes else None,
                   intervalo_media_ms=mean(intervals), intervalo_max_ms=max(intervals) if intervals else None,
                   bytes_lote_total=bytes_all, bytes_lote_com_dados=bytes_data,
                   lotes_minuto_janela_parada=window_counts, aceites_por_particao=accepted)
    latencies, detections, suspicions = [], [], []
    for line in read(folder / "HeartbeatCheck.txt").splitlines():
        f = fields(line)
        if line.startswith("LATENCIA;") and int(f["amostras"]):
            latencies.append((int(f["amostras"]), float(f["media_ms"]), float(f["max_ms"])))
        elif line.startswith("SUSPEITA;"):
            f["participante"] = line.split(";")[1]
            f["particao"] = line.split(";")[2]
            suspicions.append(f)
            if f["detecao_ms"] != "NA":
                detections.append(int(f["detecao_ms"]))
        elif line.startswith("RESUMO;"):
            summary["checker"] = f
        elif line.startswith("BYTES;"):
            summary["bytes_por_registo"] = f
    total = sum(x[0] for x in latencies)
    summary.update(latencia_media_ms=sum(x[0]*x[1] for x in latencies)/total if total else None,
                   latencia_max_ms=max((x[2] for x in latencies), default=None),
                   detecoes_ms=detections, suspeitas=suspicions)
    console = read(folder / "produtor-console.txt")
    summary["enviadas_por_particao"] = [console.count("Publicado em rep04-in-p" + str(k) + ":") for k in range(3)]
    summaries[folder.name] = summary
(root / "resumo.json").write_text(json.dumps(summaries, ensure_ascii=False, indent=2), encoding="utf-8")
print("Resumidos", len(summaries), "ensaios.")
for name, s in summaries.items():
    print(name, "lotes", s["lotes_por_particao"], "latencia", s["latencia_media_ms"],
          "detecao", s["detecoes_ms"], "falsas", s["checker"]["falsas_suspeitas"])
