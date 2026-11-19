#!/usr/bin/env python3
"""Validate a SmartFridge YOLO dataset, fine-tune YOLO11s, validate and export TFLite."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

CLASSES = [
    "Алкоголь",
    "Конфеты",
    "Консервы",
    "Шоколад",
    "Десерт",
    "Сушёные продукты",
    "Сухофрукты",
    "Напитки",
    "Жвачка",
    "Растворимые напитки",
    "Лапша быстрого приготовления",
    "Молоко",
    "Средства гигиены",
    "Снеки",
    "Приправы",
    "Канцелярия",
    "Бумажные салфетки",
]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def validate_split(root: Path, split: str) -> list[int]:
    images_dir = root / "images" / split
    labels_dir = root / "labels" / split
    if not images_dir.is_dir() or not labels_dir.is_dir():
        raise FileNotFoundError(
            f"Не найдены папки {images_dir} и/или {labels_dir}. "
            "Ожидается структура dataset/images/{train,val} и dataset/labels/{train,val}."
        )

    images = [p for p in images_dir.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS]
    if not images:
        raise ValueError(f"В {images_dir} нет изображений.")

    counts = [0] * len(CLASSES)
    errors: list[str] = []
    for image in images:
        label_file = labels_dir / f"{image.stem}.txt"
        if not label_file.exists():
            errors.append(f"Нет TXT-разметки: {label_file}")
            continue
        for line_number, line in enumerate(label_file.read_text(encoding="utf-8-sig").splitlines(), 1):
            if not line.strip():
                continue
            parts = line.split()
            if len(parts) != 5:
                errors.append(f"{label_file}:{line_number}: ожидалось 5 значений, получено {len(parts)}")
                continue
            try:
                class_id = int(float(parts[0]))
                x, y, width, height = map(float, parts[1:])
            except ValueError:
                errors.append(f"{label_file}:{line_number}: нечисловые значения")
                continue
            if class_id < 0 or class_id >= len(CLASSES):
                errors.append(f"{label_file}:{line_number}: class_id {class_id} вне диапазона 0..16")
                continue
            if not all(0.0 <= value <= 1.0 for value in (x, y, width, height)) or width <= 0 or height <= 0:
                errors.append(f"{label_file}:{line_number}: координаты должны быть в диапазоне 0..1, ширина/высота > 0")
                continue
            counts[class_id] += 1

    if errors:
        preview = "\n".join(errors[:30])
        raise ValueError(f"Ошибки датасета ({len(errors)}):\n{preview}")

    print(f"\n{split}: изображений {len(images)}")
    for class_id, count in enumerate(counts):
        print(f"  {class_id:02d} {CLASSES[class_id]}: {count} объектов")
    return counts


def write_data_yaml(root: Path) -> Path:
    try:
        import yaml
    except ImportError as exc:
        raise RuntimeError("Не установлен PyYAML. Выполни: pip install -r requirements.txt") from exc

    config = {
        "path": str(root.resolve()),
        "train": "images/train",
        "val": "images/val",
        "names": {index: name for index, name in enumerate(CLASSES)},
    }
    output = root / "smartfridge_food.yaml"
    output.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True, help="Папка dataset с images/ и labels/")
    parser.add_argument("--weights", default="yolo11s.pt", help="yolo11s.pt либо собственный совместимый 17-классовый best.pt")
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--imgsz", type=int, default=640)
    args = parser.parse_args()

    root = Path(args.dataset).expanduser().resolve()
    train_counts = validate_split(root, "train")
    validate_split(root, "val")
    missing = [CLASSES[i] for i, count in enumerate(train_counts) if count == 0]
    if missing:
        raise ValueError("В train отсутствуют объекты классов: " + ", ".join(missing))

    data_yaml = write_data_yaml(root)
    print(f"\nКонфигурация датасета: {data_yaml}")

    try:
        import torch
        from ultralytics import YOLO
    except ImportError as exc:
        raise RuntimeError("Зависимости не установлены. Выполни: pip install -r requirements.txt") from exc

    device = 0 if torch.cuda.is_available() else "cpu"
    if device == "cpu":
        print("ВНИМАНИЕ: CUDA GPU не найден. На CPU обучение будет заметно медленнее; для быстрого результата используй Colab GPU.")

    weights = Path(args.weights).expanduser()
    if weights.exists():
        print(f"Используются локальные веса: {weights.resolve()}")
    else:
        print(f"Используются предобученные веса Ultralytics: {args.weights}")

    model = YOLO(str(weights) if weights.exists() else args.weights)
    results = model.train(
        data=str(data_yaml),
        epochs=max(1, args.epochs),
        imgsz=args.imgsz,
        batch=args.batch,
        patience=15,
        workers=4,
        device=device,
        optimizer="AdamW",
        lr0=0.001,
        cos_lr=True,
        close_mosaic=10,
        mosaic=0.5,
        degrees=5.0,
        translate=0.1,
        scale=0.2,
        fliplr=0.5,
        hsv_h=0.015,
        hsv_s=0.4,
        hsv_v=0.25,
        amp=True,
        project="runs/smartfridge",
        name="yolo11s_food",
        exist_ok=True,
        plots=True,
        verbose=True,
    )

    save_dir = Path(results.save_dir)
    best_pt = save_dir / "weights" / "best.pt"
    if not best_pt.is_file():
        raise FileNotFoundError(f"Обучение завершилось, но best.pt не найден: {best_pt}")

    print(f"\nЛучшая модель: {best_pt}")
    trained = YOLO(str(best_pt))
    print("\nПовторная проверка validation...")
    trained.val(data=str(data_yaml), imgsz=args.imgsz, split="val", plots=True)

    print("\nЭкспорт в TFLite...")
    exported_path = trained.export(format="tflite", imgsz=args.imgsz, int8=False)
    print("\nГОТОВО.")
    print(f"PyTorch checkpoint: {best_pt}")
    print(f"TFLite export: {exported_path}")
    print("Проверь модель на отдельных тестовых фото, затем только при улучшении замени app/src/main/assets/best.tflite.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ОШИБКА: {error}", file=sys.stderr)
        raise
