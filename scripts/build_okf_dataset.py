#!/usr/bin/env python3
"""
EduPulse — Open Knowledge Format (OKF) Compiler
================================================
Parses NCERT Class 11 & 12 textbooks, JEE/NEET syllabi, and PYQ PDFs from data/
into structured Open Knowledge Format (.okf.md) nodes and an indexed SQLite database.

Output:
  - data/okf/cards/         -> Standalone .okf.md files with YAML frontmatter + Markdown
  - data/okf/knowledge.db   -> SQLite FTS5 database for on-device mobile lookup
  - app/src/main/assets/knowledge.db -> Bundled into Android app APK
"""

import os
import re
import sys
import glob
import json
import sqlite3
import shutil
import pypdf
import yaml

if sys.stdout.encoding.lower() != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "data"))
OUT_DIR = os.path.abspath(os.path.join(DATA_DIR, "okf"))
CARDS_DIR = os.path.join(OUT_DIR, "cards")
DB_PATH = os.path.join(OUT_DIR, "knowledge.db")
ASSETS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets"))
ASSET_DB_PATH = os.path.join(ASSETS_DIR, "knowledge.db")

# Known physics chapters mapping for NCERT Class 11 & 12
PHYSICS_CHAPTERS_11 = {
    "keph101": {"ch": 1, "title": "Units and Measurement", "unit": "Unit I: Physical World and Measurement"},
    "keph102": {"ch": 2, "title": "Motion in a Straight Line", "unit": "Unit II: Kinematics"},
    "keph103": {"ch": 3, "title": "Motion in a Plane", "unit": "Unit II: Kinematics"},
    "keph104": {"ch": 4, "title": "Laws of Motion", "unit": "Unit III: Laws of Motion"},
    "keph105": {"ch": 5, "title": "Work, Energy and Power", "unit": "Unit IV: Work, Energy and Power"},
    "keph106": {"ch": 6, "title": "Systems of Particles and Rotational Motion", "unit": "Unit V: Motion of System of Particles"},
    "keph107": {"ch": 7, "title": "Gravitation", "unit": "Unit VI: Gravitation"},
    "keph201": {"ch": 8, "title": "Mechanical Properties of Solids", "unit": "Unit VII: Properties of Bulk Matter"},
    "keph202": {"ch": 9, "title": "Mechanical Properties of Fluids", "unit": "Unit VII: Properties of Bulk Matter"},
    "keph203": {"ch": 10, "title": "Thermal Properties of Matter", "unit": "Unit VII: Properties of Bulk Matter"},
    "keph204": {"ch": 11, "title": "Thermodynamics", "unit": "Unit VIII: Thermodynamics"},
    "keph205": {"ch": 12, "title": "Kinetic Theory of Gases", "unit": "Unit IX: Kinetic Theory of Gases"},
    "keph206": {"ch": 13, "title": "Oscillations", "unit": "Unit X: Oscillations and Waves"},
    "keph207": {"ch": 14, "title": "Waves", "unit": "Unit X: Oscillations and Waves"},
}

# Domain-specific canonical formulas and variables for high-frequency JEE/NEET topics
HIGH_YIELD_DOMAINS = {
    "keph102": {
        "target_quantities": [
            {"symbol": "u", "name": "initial velocity", "units": ["m/s", "km/h"], "dimension": "LT^-1"},
            {"symbol": "v", "name": "final velocity", "units": ["m/s", "km/h"], "dimension": "LT^-1"},
            {"symbol": "a", "name": "acceleration / retardation", "units": ["m/s²", "m/s^2"], "dimension": "LT^-2"},
            {"symbol": "s", "name": "displacement / distance", "units": ["m", "cm", "km"], "dimension": "L"},
            {"symbol": "t", "name": "time", "units": ["s", "sec", "min"], "dimension": "T"},
        ],
        "canonical_formulas": [
            {"id": "first_equation", "formula": "v = u + a * t", "latex": "v = u + at"},
            {"id": "second_equation", "formula": "s = u * t + 0.5 * a * t^2", "latex": "s = ut + \\frac{1}{2}at^2"},
            {"id": "third_equation", "formula": "v^2 = u^2 + 2 * a * s", "latex": "v^2 = u^2 + 2as"},
            {"id": "stopping_dist", "formula": "s = (u^2) / (2 * a)", "latex": "s = \\frac{u^2}{2a}", "constraints": ["v == 0"]},
            {"id": "stopping_time", "formula": "t = u / a", "latex": "t = \\frac{u}{a}", "constraints": ["v == 0"]}
        ],
        "common_ocr_confusions": [
            {"corrupt": "isn", "intended": "15N", "entity": "force"},
            {"corrupt": "l5N", "intended": "15N", "entity": "force"},
            {"corrupt": "io m/s", "intended": "10 m/s", "entity": "velocity"},
            {"corrupt": "u seconds", "intended": "4 seconds", "entity": "time"},
            {"corrupt": "skg", "intended": "5 kg", "entity": "mass"}
        ],
        "exam_traps": [
            "NEET: Retarding force means acceleration is negative in standard kinematics equations.",
            "JEE: If speed is doubled, stopping distance quadruples (s proportional to u^2)."
        ]
    },
    "keph103": {
        "target_quantities": [
            {"symbol": "u", "name": "initial speed", "units": ["m/s"], "dimension": "LT^-1"},
            {"symbol": "theta", "name": "angle of projection", "units": ["deg", "rad"], "dimension": "1"},
            {"symbol": "R", "name": "horizontal range", "units": ["m"], "dimension": "L"},
            {"symbol": "H", "name": "maximum height", "units": ["m"], "dimension": "L"},
            {"symbol": "T", "name": "time of flight", "units": ["s"], "dimension": "T"},
            {"symbol": "g", "name": "acceleration due to gravity", "units": ["m/s²", "m/s^2"], "dimension": "LT^-2"}
        ],
        "canonical_formulas": [
            {"id": "time_of_flight", "formula": "T = (2 * u * sin(theta)) / g", "latex": "T = \\frac{2u \\sin\\theta}{g}"},
            {"id": "max_height", "formula": "H = (u^2 * (sin(theta))^2) / (2 * g)", "latex": "H = \\frac{u^2 \\sin^2\\theta}{2g}"},
            {"id": "horizontal_range", "formula": "R = (u^2 * sin(2 * theta)) / g", "latex": "R = \\frac{u^2 \\sin(2\\theta)}{g}"}
        ],
        "common_ocr_confusions": [
            {"corrupt": "9.8 m/s", "intended": "9.8 m/s²", "entity": "gravity"},
            {"corrupt": "3O deg", "intended": "30 deg", "entity": "angle"}
        ],
        "exam_traps": [
            "Range is maximum at 45 degrees: R_max = u^2 / g.",
            "Two complementary angles (theta and 90 - theta) yield the exact same horizontal range R."
        ]
    },
    "keph104": {
        "target_quantities": [
            {"symbol": "F", "name": "force", "units": ["N", "kN"], "dimension": "MLT^-2"},
            {"symbol": "m", "name": "mass", "units": ["kg", "g"], "dimension": "M"},
            {"symbol": "a", "name": "acceleration", "units": ["m/s²", "m/s^2"], "dimension": "LT^-2"},
            {"symbol": "p", "name": "linear momentum", "units": ["kg m/s"], "dimension": "MLT^-1"},
            {"symbol": "mu", "name": "coefficient of friction", "units": [""], "dimension": "1"}
        ],
        "canonical_formulas": [
            {"id": "newton_second_law", "formula": "F = m * a", "latex": "F = ma"},
            {"id": "momentum", "formula": "p = m * v", "latex": "p = mv"},
            {"id": "impulse", "formula": "J = F * delta_t", "latex": "J = F \\Delta t = \\Delta p"},
            {"id": "friction", "formula": "f_max = mu * N", "latex": "f_{max} = \\mu N"}
        ],
        "common_ocr_confusions": [
            {"corrupt": "isn", "intended": "15N", "entity": "force"},
            {"corrupt": "3000N", "intended": "3000 N", "entity": "force"},
            {"corrupt": "l500kg", "intended": "1500 kg", "entity": "mass"}
        ],
        "exam_traps": [
            "Apparent weight in an accelerating elevator: R = m(g + a) upwards, R = m(g - a) downwards.",
            "Static friction is self-adjusting up to limiting friction f_s <= mu_s * N."
        ]
    },
    "keph105": {
        "target_quantities": [
            {"symbol": "W", "name": "work done", "units": ["J", "kJ"], "dimension": "ML^2T^-2"},
            {"symbol": "K", "name": "kinetic energy", "units": ["J", "kJ"], "dimension": "ML^2T^-2"},
            {"symbol": "U", "name": "potential energy", "units": ["J", "kJ"], "dimension": "ML^2T^-2"},
            {"symbol": "P", "name": "power", "units": ["W", "kW"], "dimension": "ML^2T^-3"}
        ],
        "canonical_formulas": [
            {"id": "work", "formula": "W = F * d * cos(theta)", "latex": "W = \\vec{F} \\cdot \\vec{d}"},
            {"id": "kinetic_energy", "formula": "K = 0.5 * m * v^2", "latex": "K = \\frac{1}{2}mv^2 = \\frac{p^2}{2m}"},
            {"id": "work_energy_theorem", "formula": "W_net = delta_K", "latex": "W_{net} = K_f - K_i"},
            {"id": "power", "formula": "P = W / t", "latex": "P = \\frac{W}{t} = \\vec{F} \\cdot \\vec{v}"}
        ],
        "common_ocr_confusions": [
            {"corrupt": "l00J", "intended": "100 J", "entity": "energy"},
            {"corrupt": "skW", "intended": "5 kW", "entity": "power"}
        ],
        "exam_traps": [
            "Work done by centripetal force is always zero because force is perpendicular to velocity."
        ]
    }
}


def extract_ncert_summary(pdf_path):
    """Extract the summary or points to ponder section from the end of an NCERT chapter."""
    try:
        reader = pypdf.PdfReader(pdf_path)
        num_pages = len(reader.pages)
        start_page = max(0, num_pages - 5)
        tail_text = "\n".join([reader.pages[i].extract_text() or "" for i in range(start_page, num_pages)])
        
        m = re.search(r'(SUMMARY|POINTS TO PONDER|SUMMARY\s*\n)(.*?)(EXERCISES|Exercises|ANSWERS|$)', tail_text, re.DOTALL | re.IGNORECASE)
        if m:
            clean = m.group(2).strip()
            return clean[:2000]
        return ""
    except Exception as e:
        return ""


def parse_pyqs(pyq_pdf_path, max_q=5):
    """Extract sample questions from official PYQ PDFs."""
    questions = []
    try:
        reader = pypdf.PdfReader(pyq_pdf_path)
        all_text = ""
        for p in reader.pages[:4]:
            t = p.extract_text()
            if t:
                all_text += "\n" + t
        
        q_blocks = re.split(r'\n\s*Q(\d+)\.\s*', all_text)
        if len(q_blocks) > 1:
            for i in range(1, len(q_blocks), 2):
                q_num = q_blocks[i]
                q_text = q_blocks[i+1].strip()
                lines = q_text.split('\n')
                prompt = lines[0] if lines else q_text[:200]
                questions.append({
                    "q_num": q_num,
                    "text": q_text[:500],
                    "prompt": prompt
                })
                if len(questions) >= max_q:
                    break
    except Exception as e:
        pass
    return questions


def generate_okf_card(node_id, meta, high_yield, summary_text, pyqs):
    """Generate a single Open Knowledge Format (.okf.md) file."""
    yaml_dict = {
        "id": node_id,
        "domain": "physics",
        "subject": "physics",
        "class": meta.get("class", 11),
        "chapter_num": meta["ch"],
        "chapter_title": meta["title"],
        "unit": meta["unit"],
        "exam_tags": ["JEE_Main", "NEET"],
        "target_quantities": high_yield.get("target_quantities", []),
        "canonical_formulas": high_yield.get("canonical_formulas", []),
        "common_ocr_confusions": high_yield.get("common_ocr_confusions", []),
        "exam_traps": high_yield.get("exam_traps", [])
    }
    
    yaml_header = yaml.dump(yaml_dict, sort_keys=False, allow_unicode=True)
    
    md_content = f"""---
{yaml_header}---

# {meta['title']} ({meta['unit']})
> **Standard:** Class {meta.get('class', 11)} NCERT Physics | Target: JEE Main & NEET

## 1. Key Principles & Conceptual Summary
{summary_text if summary_text else "Core conceptual framework based on NCERT guidelines for " + meta['title'] + "."}

## 2. Canonical Formulas & Constraints
"""
    for f in high_yield.get("canonical_formulas", []):
        md_content += f"- **{f.get('id', 'formula')}**: `${f.get('latex', f.get('formula', ''))}$`\n"

    if high_yield.get("exam_traps"):
        md_content += "\n## 3. High-Yield Exam Traps (JEE & NEET)\n"
        for trap in high_yield["exam_traps"]:
            md_content += f"- ⚠️ {trap}\n"

    if pyqs:
        md_content += "\n## 4. Representative Previous Year Questions (PYQs)\n"
        for q in pyqs:
            md_content += f"### PYQ Q{q['q_num']}\n```text\n{q['text']}\n```\n"

    return yaml_dict, md_content


def main():
    print("=" * 60)
    print("EduPulse: Building Open Knowledge Format (OKF) Knowledge Base")
    print("=" * 60)

    os.makedirs(CARDS_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    cursor.execute("""
    CREATE TABLE okf_nodes (
        id TEXT PRIMARY KEY,
        subject TEXT,
        class_level INTEGER,
        chapter_title TEXT,
        unit_name TEXT,
        frontmatter_yaml TEXT,
        body_markdown TEXT,
        keywords TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE okf_formulas (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        node_id TEXT,
        formula_id TEXT,
        formula_latex TEXT,
        formula_code TEXT,
        constraints TEXT,
        FOREIGN KEY (node_id) REFERENCES okf_nodes(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE ocr_confusions (
        corrupt_token TEXT PRIMARY KEY,
        intended_token TEXT,
        entity_type TEXT,
        node_id TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE pyq_questions (
        id TEXT PRIMARY KEY,
        exam TEXT,
        year INTEGER,
        shift TEXT,
        subject TEXT,
        question_text TEXT,
        node_id TEXT
    );
    """)

    cursor.execute("""
    CREATE VIRTUAL TABLE okf_fts USING fts5(
        id UNINDEXED,
        subject,
        chapter_title,
        unit_name,
        keywords,
        body_markdown
    );
    """)

    pyq_file = os.path.join(DATA_DIR, "Jee_pyq", "2024", "01feb_1.pdf")
    sample_pyqs = []
    if os.path.exists(pyq_file):
        print(f"[*] Extracting sample PYQs from {os.path.basename(pyq_file)}...")
        sample_pyqs = parse_pyqs(pyq_file, max_q=5)
        print(f"    Extracted {len(sample_pyqs)} sample PYQs.")

    total_cards = 0
    print("\n[*] Processing Class 11 Physics NCERT chapters...")
    for slug, meta in PHYSICS_CHAPTERS_11.items():
        meta["class"] = 11
        folder = "11_phy_1" if meta["ch"] <= 7 else "11_phy_2"
        pdf_path = os.path.join(DATA_DIR, folder, f"{slug}.pdf")
        
        summary = ""
        if os.path.exists(pdf_path):
            summary = extract_ncert_summary(pdf_path)

        high_yield = HIGH_YIELD_DOMAINS.get(slug, {
            "target_quantities": [],
            "canonical_formulas": [],
            "common_ocr_confusions": [],
            "exam_traps": []
        })

        node_id = f"jee_neet.phy.11.{slug}"
        yaml_dict, md_content = generate_okf_card(node_id, meta, high_yield, summary, sample_pyqs if slug == "keph102" else [])

        card_file = os.path.join(CARDS_DIR, f"{node_id}.okf.md")
        with open(card_file, "w", encoding="utf-8") as f:
            f.write(md_content)

        keywords_list = [meta["title"], meta["unit"], "physics", f"class {meta['class']}"]
        for q in high_yield["target_quantities"]:
            keywords_list.append(q["name"])
            keywords_list.append(q["symbol"])
            keywords_list.extend(q.get("units", []))
        for f in high_yield["canonical_formulas"]:
            keywords_list.append(f.get("formula", ""))
        keywords_str = " ".join(set(keywords_list))

        cursor.execute(
            "INSERT INTO okf_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (node_id, "physics", meta["class"], meta["title"], meta["unit"], yaml.dump(yaml_dict), md_content, keywords_str)
        )

        cursor.execute(
            "INSERT INTO okf_fts VALUES (?, ?, ?, ?, ?, ?)",
            (node_id, "physics", meta["title"], meta["unit"], keywords_str, md_content)
        )

        for f in high_yield["canonical_formulas"]:
            cursor.execute(
                "INSERT INTO okf_formulas (node_id, formula_id, formula_latex, formula_code, constraints) VALUES (?, ?, ?, ?, ?)",
                (node_id, f.get("id"), f.get("latex"), f.get("formula"), json.dumps(f.get("constraints", [])))
            )

        for ocr in high_yield["common_ocr_confusions"]:
            cursor.execute(
                "INSERT OR REPLACE INTO ocr_confusions VALUES (?, ?, ?, ?)",
                (ocr["corrupt"], ocr["intended"], ocr.get("entity", ""), node_id)
            )

        total_cards += 1
        print(f"  [OK] Compiled OKF: {node_id} ({meta['title']})")

    for q in sample_pyqs:
        q_id = f"pyq.jee.2024.01feb_1.q{q['q_num']}"
        cursor.execute(
            "INSERT OR REPLACE INTO pyq_questions VALUES (?, ?, ?, ?, ?, ?, ?)",
            (q_id, "JEE_Main", 2024, "01 Feb Shift 1", "physics", q["text"], "jee_neet.phy.11.keph102")
        )

    conn.commit()
    conn.close()

    shutil.copyfile(DB_PATH, ASSET_DB_PATH)

    db_size_kb = os.path.getsize(DB_PATH) / 1024
    asset_db_size_kb = os.path.getsize(ASSET_DB_PATH) / 1024

    print("\n" + "=" * 60)
    print("Compilation Complete!")
    print(f"[*] Total OKF Cards Generated: {total_cards}")
    print(f"[*] Standalone Markdown cards saved in: {CARDS_DIR}")
    print(f"[*] Compiled SQLite FTS5 database: {DB_PATH} ({db_size_kb:.2f} KB)")
    print(f"[*] Copied to Android Assets: {ASSET_DB_PATH} ({asset_db_size_kb:.2f} KB)")
    print("=" * 60)


if __name__ == "__main__":
    main()
