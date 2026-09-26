#!/usr/bin/env python3
"""
EduPulse — Open Knowledge Format (OKF) Compiler
================================================
Parses NCERT Class 11 & 12 textbooks (Physics, Chemistry, Biology),
NEET syllabus, and NEET PYQ PDFs from data/ into structured Open Knowledge
Format (.okf.md) nodes and an indexed SQLite database.

Focus: NEET (Physics + Chemistry + Biology). JEE retained only as a tag
       on topics that overlap both exams.

OKF Tree Structure (proper graph):
  Each node has:
    - node_type: root | subject | class_group | chapter | concept
    - parent_id: ID of parent node
    - children: list of child node IDs
    - related: list of cross-chapter related node IDs

Output:
  - data/okf/cards/         -> Standalone .okf.md files (YAML frontmatter + Markdown)
  - data/okf/knowledge.db   -> SQLite database (okf_nodes, okf_formulas, okf_quantities,
                               okf_exam_traps, ocr_confusions, pyq_questions, okf_edges)
  - app/src/main/assets/knowledge.db -> Bundled into Android APK
"""

import os
import re
import sys
import json
import sqlite3
import shutil
import glob
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

# ---------------------------------------------------------------------------
# Chapter Catalogues  (NCERT official slugs → metadata)
# ---------------------------------------------------------------------------

PHYSICS_11 = {
    "keph101": {"ch": 1,  "title": "Units and Measurement",                        "unit": "Unit I: Physical World and Measurement",   "class": 11, "folder": "11_phy_1"},
    "keph102": {"ch": 2,  "title": "Motion in a Straight Line",                    "unit": "Unit II: Kinematics",                       "class": 11, "folder": "11_phy_1"},
    "keph103": {"ch": 3,  "title": "Motion in a Plane",                            "unit": "Unit II: Kinematics",                       "class": 11, "folder": "11_phy_1"},
    "keph104": {"ch": 4,  "title": "Laws of Motion",                               "unit": "Unit III: Laws of Motion",                  "class": 11, "folder": "11_phy_1"},
    "keph105": {"ch": 5,  "title": "Work, Energy and Power",                       "unit": "Unit IV: Work, Energy and Power",           "class": 11, "folder": "11_phy_1"},
    "keph106": {"ch": 6,  "title": "Systems of Particles and Rotational Motion",   "unit": "Unit V: Motion of System of Particles",    "class": 11, "folder": "11_phy_1"},
    "keph107": {"ch": 7,  "title": "Gravitation",                                  "unit": "Unit VI: Gravitation",                      "class": 11, "folder": "11_phy_1"},
    "keph201": {"ch": 8,  "title": "Mechanical Properties of Solids",              "unit": "Unit VII: Properties of Bulk Matter",       "class": 11, "folder": "11_phy_2"},
    "keph202": {"ch": 9,  "title": "Mechanical Properties of Fluids",              "unit": "Unit VII: Properties of Bulk Matter",       "class": 11, "folder": "11_phy_2"},
    "keph203": {"ch": 10, "title": "Thermal Properties of Matter",                 "unit": "Unit VII: Properties of Bulk Matter",       "class": 11, "folder": "11_phy_2"},
    "keph204": {"ch": 11, "title": "Thermodynamics",                               "unit": "Unit VIII: Thermodynamics",                 "class": 11, "folder": "11_phy_2"},
    "keph205": {"ch": 12, "title": "Kinetic Theory of Gases",                      "unit": "Unit IX: Kinetic Theory of Gases",          "class": 11, "folder": "11_phy_2"},
    "keph206": {"ch": 13, "title": "Oscillations",                                 "unit": "Unit X: Oscillations and Waves",            "class": 11, "folder": "11_phy_2"},
    "keph207": {"ch": 14, "title": "Waves",                                        "unit": "Unit X: Oscillations and Waves",            "class": 11, "folder": "11_phy_2"},
}

PHYSICS_12 = {
    "leph101": {"ch": 1,  "title": "Electric Charges and Fields",                  "unit": "Unit I: Electrostatics",                    "class": 12, "folder": "12_phy_1"},
    "leph102": {"ch": 2,  "title": "Electrostatic Potential and Capacitance",      "unit": "Unit I: Electrostatics",                    "class": 12, "folder": "12_phy_1"},
    "leph103": {"ch": 3,  "title": "Current Electricity",                          "unit": "Unit II: Current Electricity",              "class": 12, "folder": "12_phy_1"},
    "leph104": {"ch": 4,  "title": "Moving Charges and Magnetism",                 "unit": "Unit III: Magnetic Effects of Current",     "class": 12, "folder": "12_phy_1"},
    "leph105": {"ch": 5,  "title": "Magnetism and Matter",                         "unit": "Unit III: Magnetic Effects of Current",     "class": 12, "folder": "12_phy_1"},
    "leph106": {"ch": 6,  "title": "Electromagnetic Induction",                    "unit": "Unit IV: Electromagnetic Induction & AC",   "class": 12, "folder": "12_phy_1"},
    "leph107": {"ch": 7,  "title": "Alternating Current",                          "unit": "Unit IV: Electromagnetic Induction & AC",   "class": 12, "folder": "12_phy_1"},
    "leph108": {"ch": 8,  "title": "Electromagnetic Waves",                        "unit": "Unit V: Electromagnetic Waves",             "class": 12, "folder": "12_phy_1"},
    "leph201": {"ch": 9,  "title": "Ray Optics and Optical Instruments",           "unit": "Unit VI: Optics",                           "class": 12, "folder": "12_phy_2"},
    "leph202": {"ch": 10, "title": "Wave Optics",                                  "unit": "Unit VI: Optics",                           "class": 12, "folder": "12_phy_2"},
    "leph203": {"ch": 11, "title": "Dual Nature of Radiation and Matter",          "unit": "Unit VII: Dual Nature of Radiation",        "class": 12, "folder": "12_phy_2"},
    "leph204": {"ch": 12, "title": "Atoms",                                        "unit": "Unit VIII: Atoms and Nuclei",               "class": 12, "folder": "12_phy_2"},
    "leph205": {"ch": 13, "title": "Nuclei",                                       "unit": "Unit VIII: Atoms and Nuclei",               "class": 12, "folder": "12_phy_2"},
    "leph206": {"ch": 14, "title": "Semiconductor Electronics",                    "unit": "Unit IX: Electronic Devices",              "class": 12, "folder": "12_phy_2"},
}

CHEMISTRY_11 = {
    "kech101": {"ch": 1,  "title": "Some Basic Concepts of Chemistry",             "unit": "Unit I: Basic Concepts",                    "class": 11, "folder": "11_che_1"},
    "kech102": {"ch": 2,  "title": "Structure of Atom",                            "unit": "Unit II: Structure of Atom",                "class": 11, "folder": "11_che_1"},
    "kech103": {"ch": 3,  "title": "Classification of Elements and Periodicity",   "unit": "Unit III: Periodicity",                     "class": 11, "folder": "11_che_1"},
    "kech104": {"ch": 4,  "title": "Chemical Bonding and Molecular Structure",     "unit": "Unit IV: Chemical Bonding",                 "class": 11, "folder": "11_che_1"},
    "kech105": {"ch": 5,  "title": "Thermodynamics",                               "unit": "Unit V: Thermodynamics",                    "class": 11, "folder": "11_che_1"},
    "kech106": {"ch": 6,  "title": "Equilibrium",                                  "unit": "Unit VI: Equilibrium",                      "class": 11, "folder": "11_che_1"},
}

CHEMISTRY_12 = {
    "lech101": {"ch": 1,  "title": "Solutions",                                    "unit": "Unit I: Solutions",                         "class": 12, "folder": "12_che_1"},
    "lech102": {"ch": 2,  "title": "Electrochemistry",                             "unit": "Unit II: Electrochemistry",                 "class": 12, "folder": "12_che_1"},
    "lech103": {"ch": 3,  "title": "Chemical Kinetics",                            "unit": "Unit III: Chemical Kinetics",               "class": 12, "folder": "12_che_1"},
    "lech104": {"ch": 4,  "title": "d- and f-Block Elements",                      "unit": "Unit IV: d and f Block Elements",           "class": 12, "folder": "12_che_1"},
    "lech105": {"ch": 5,  "title": "Coordination Compounds",                       "unit": "Unit V: Coordination Compounds",            "class": 12, "folder": "12_che_1"},
    "lech201": {"ch": 6,  "title": "Haloalkanes and Haloarenes",                   "unit": "Unit VI: Organic Halogen Compounds",        "class": 12, "folder": "12_che_2"},
    "lech202": {"ch": 7,  "title": "Alcohols, Phenols and Ethers",                 "unit": "Unit VII: Oxygen-containing Organics",     "class": 12, "folder": "12_che_2"},
    "lech203": {"ch": 8,  "title": "Aldehydes, Ketones and Carboxylic Acids",      "unit": "Unit VII: Oxygen-containing Organics",     "class": 12, "folder": "12_che_2"},
    "lech204": {"ch": 9,  "title": "Amines",                                       "unit": "Unit VIII: Nitrogen-containing Organics",  "class": 12, "folder": "12_che_2"},
    "lech205": {"ch": 10, "title": "Biomolecules",                                 "unit": "Unit IX: Biomolecules",                     "class": 12, "folder": "12_che_2"},
}

BIOLOGY_11 = {
    "kebo101": {"ch": 1,  "title": "The Living World",                             "unit": "Unit I: Diversity in Living World",         "class": 11, "folder": "11_bio"},
    "kebo102": {"ch": 2,  "title": "Biological Classification",                    "unit": "Unit I: Diversity in Living World",         "class": 11, "folder": "11_bio"},
    "kebo103": {"ch": 3,  "title": "Plant Kingdom",                                "unit": "Unit I: Diversity in Living World",         "class": 11, "folder": "11_bio"},
    "kebo104": {"ch": 4,  "title": "Animal Kingdom",                               "unit": "Unit I: Diversity in Living World",         "class": 11, "folder": "11_bio"},
    "kebo105": {"ch": 5,  "title": "Morphology of Flowering Plants",               "unit": "Unit II: Structural Organisation",         "class": 11, "folder": "11_bio"},
    "kebo106": {"ch": 6,  "title": "Anatomy of Flowering Plants",                  "unit": "Unit II: Structural Organisation",         "class": 11, "folder": "11_bio"},
    "kebo107": {"ch": 7,  "title": "Structural Organisation in Animals",            "unit": "Unit II: Structural Organisation",         "class": 11, "folder": "11_bio"},
    "kebo108": {"ch": 8,  "title": "Cell: The Unit of Life",                       "unit": "Unit III: Cell Structure and Function",    "class": 11, "folder": "11_bio"},
    "kebo109": {"ch": 9,  "title": "Biomolecules",                                 "unit": "Unit III: Cell Structure and Function",    "class": 11, "folder": "11_bio"},
    "kebo110": {"ch": 10, "title": "Cell Cycle and Cell Division",                 "unit": "Unit III: Cell Structure and Function",    "class": 11, "folder": "11_bio"},
    "kebo111": {"ch": 11, "title": "Photosynthesis in Higher Plants",              "unit": "Unit IV: Plant Physiology",                "class": 11, "folder": "11_bio"},
    "kebo112": {"ch": 12, "title": "Respiration in Plants",                        "unit": "Unit IV: Plant Physiology",                "class": 11, "folder": "11_bio"},
    "kebo113": {"ch": 13, "title": "Plant Growth and Development",                 "unit": "Unit IV: Plant Physiology",                "class": 11, "folder": "11_bio"},
    "kebo114": {"ch": 14, "title": "Breathing and Exchange of Gases",              "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
    "kebo115": {"ch": 15, "title": "Body Fluids and Circulation",                  "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
    "kebo116": {"ch": 16, "title": "Excretory Products and their Elimination",     "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
    "kebo117": {"ch": 17, "title": "Locomotion and Movement",                      "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
    "kebo118": {"ch": 18, "title": "Neural Control and Coordination",              "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
    "kebo119": {"ch": 19, "title": "Chemical Coordination and Integration",        "unit": "Unit V: Human Physiology",                 "class": 11, "folder": "11_bio"},
}

BIOLOGY_12 = {
    "lebo101": {"ch": 1,  "title": "Sexual Reproduction in Flowering Plants",      "unit": "Unit I: Reproduction",                     "class": 12, "folder": "12_bio"},
    "lebo102": {"ch": 2,  "title": "Human Reproduction",                           "unit": "Unit I: Reproduction",                     "class": 12, "folder": "12_bio"},
    "lebo103": {"ch": 3,  "title": "Reproductive Health",                          "unit": "Unit I: Reproduction",                     "class": 12, "folder": "12_bio"},
    "lebo104": {"ch": 4,  "title": "Principles of Inheritance and Variation",      "unit": "Unit II: Genetics and Evolution",          "class": 12, "folder": "12_bio"},
    "lebo105": {"ch": 5,  "title": "Molecular Basis of Inheritance",               "unit": "Unit II: Genetics and Evolution",          "class": 12, "folder": "12_bio"},
    "lebo106": {"ch": 6,  "title": "Evolution",                                    "unit": "Unit II: Genetics and Evolution",          "class": 12, "folder": "12_bio"},
    "lebo107": {"ch": 7,  "title": "Human Health and Disease",                     "unit": "Unit III: Biology and Human Welfare",      "class": 12, "folder": "12_bio"},
    "lebo108": {"ch": 8,  "title": "Microbes in Human Welfare",                    "unit": "Unit III: Biology and Human Welfare",      "class": 12, "folder": "12_bio"},
    "lebo109": {"ch": 9,  "title": "Biotechnology: Principles and Processes",      "unit": "Unit IV: Biotechnology",                   "class": 12, "folder": "12_bio"},
    "lebo110": {"ch": 10, "title": "Biotechnology and its Applications",           "unit": "Unit IV: Biotechnology",                   "class": 12, "folder": "12_bio"},
    "lebo111": {"ch": 11, "title": "Organisms and Populations",                    "unit": "Unit V: Ecology",                          "class": 12, "folder": "12_bio"},
    "lebo112": {"ch": 12, "title": "Ecosystem",                                    "unit": "Unit V: Ecology",                          "class": 12, "folder": "12_bio"},
    "lebo113": {"ch": 13, "title": "Biodiversity and Conservation",                "unit": "Unit V: Ecology",                          "class": 12, "folder": "12_bio"},
}

# ---------------------------------------------------------------------------
# High-yield domain data: Physics (NEET focus, JEE retained as secondary tag)
# ---------------------------------------------------------------------------

PHYSICS_HIGH_YIELD = {
    "keph101": {
        "target_quantities": [
            {"symbol": "h",     "name": "Planck constant",                  "units": ["J s", "J*s"],        "dimension": "ML^2T^-1"},
            {"symbol": "L",     "name": "angular momentum",                  "units": ["kg m^2/s", "J s"],   "dimension": "ML^2T^-1"},
            {"symbol": "p",     "name": "linear momentum",                   "units": ["kg m/s", "N s"],     "dimension": "MLT^-1"},
            {"symbol": "tau",   "name": "moment of force / torque",          "units": ["N m"],               "dimension": "ML^2T^-2"},
            {"symbol": "W",     "name": "work / energy",                     "units": ["J", "N m"],          "dimension": "ML^2T^-2"},
            {"symbol": "G",     "name": "universal gravitational constant",  "units": ["N m^2/kg^2"],        "dimension": "M^-1L^3T^-2"},
            {"symbol": "eta",   "name": "coefficient of viscosity",          "units": ["Pa s", "poise"],     "dimension": "ML^-1T^-1"},
            {"symbol": "sigma", "name": "surface tension",                   "units": ["N/m", "J/m^2"],      "dimension": "MT^-2"},
        ],
        "canonical_formulas": [
            {"id": "planck_energy",         "formula": "E = h * nu => [h] = ML^2T^-1",   "latex": "E = h\\nu \\implies [h] = [M L^2 T^{-1}]"},
            {"id": "angular_momentum_def",  "formula": "L = r * p => [L] = ML^2T^-1",    "latex": "L = r \\times p \\implies [L] = [M L^2 T^{-1}]"},
            {"id": "torque_def",            "formula": "tau = r * F => [tau] = ML^2T^-2", "latex": "\\tau = r \\times F \\implies [\\tau] = [M L^2 T^{-2}]"},
            {"id": "linear_momentum_def",   "formula": "p = m * v => [p] = MLT^-1",       "latex": "p = mv \\implies [p] = [M L T^{-1}]"},
            {"id": "gravitational_constant","formula": "G = F * r^2 / (m1 * m2) => [G] = M^-1L^3T^-2", "latex": "G = \\frac{F r^2}{m_1 m_2} \\implies [G] = [M^{-1} L^3 T^{-2}]"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "Js",  "intended": "J s",  "entity": "planck_constant_unit"},
            {"corrupt": "Ns",  "intended": "N s",  "entity": "momentum_unit"},
            {"corrupt": "Nm",  "intended": "N m",  "entity": "torque_unit"},
        ],
        "exam_traps": [
            "NEET: Planck's constant (h) and angular momentum (L) have the SAME dimensions: [M L^2 T^-1].",
            "NEET: Linear momentum [M L T^-1] and torque [M L^2 T^-2] do NOT have the same dimensions.",
            "NEET: Work, torque, and energy share [M L^2 T^-2], but torque is a vector while work is scalar.",
            "NEET: Impulse (J = F*Δt) and linear momentum have identical dimensions: [M L T^-1].",
            "NEET: Surface tension and spring constant both have dimension [M T^-2].",
        ],
        "related": ["neet.phy.11.keph106", "neet.phy.11.keph107"],
    },
    "keph102": {
        "target_quantities": [
            {"symbol": "u", "name": "initial velocity",         "units": ["m/s", "km/h"],  "dimension": "LT^-1"},
            {"symbol": "v", "name": "final velocity",           "units": ["m/s", "km/h"],  "dimension": "LT^-1"},
            {"symbol": "a", "name": "acceleration / retardation","units": ["m/s²"],         "dimension": "LT^-2"},
            {"symbol": "s", "name": "displacement / distance",  "units": ["m", "km"],      "dimension": "L"},
            {"symbol": "t", "name": "time",                     "units": ["s", "sec"],     "dimension": "T"},
        ],
        "canonical_formulas": [
            {"id": "first_equation",  "formula": "v = u + a * t",               "latex": "v = u + at"},
            {"id": "second_equation", "formula": "s = u * t + 0.5 * a * t^2",  "latex": "s = ut + \\frac{1}{2}at^2"},
            {"id": "third_equation",  "formula": "v^2 = u^2 + 2 * a * s",      "latex": "v^2 = u^2 + 2as"},
            {"id": "stopping_dist",   "formula": "s = u^2 / (2 * a)",           "latex": "s = \\frac{u^2}{2a}", "constraints": ["v == 0"]},
            {"id": "stopping_time",   "formula": "t = u / a",                   "latex": "t = \\frac{u}{a}",    "constraints": ["v == 0"]},
        ],
        "common_ocr_confusions": [
            {"corrupt": "io m/s", "intended": "10 m/s", "entity": "velocity"},
            {"corrupt": "u seconds", "intended": "4 seconds", "entity": "time"},
            {"corrupt": "skg",    "intended": "5 kg",   "entity": "mass"},
        ],
        "exam_traps": [
            "NEET: Retarding force means acceleration is negative (deceleration). Use a = -ve value.",
            "NEET/JEE: If speed is doubled, stopping distance quadruples (s ∝ u²).",
            "NEET: Average velocity = (u + v)/2 only for uniform acceleration.",
        ],
        "related": ["neet.phy.11.keph103", "neet.phy.11.keph104"],
    },
    "keph103": {
        "target_quantities": [
            {"symbol": "u",     "name": "initial speed",               "units": ["m/s"],       "dimension": "LT^-1"},
            {"symbol": "theta", "name": "angle of projection",         "units": ["deg", "rad"],"dimension": "1"},
            {"symbol": "R",     "name": "horizontal range",            "units": ["m"],         "dimension": "L"},
            {"symbol": "H",     "name": "maximum height",              "units": ["m"],         "dimension": "L"},
            {"symbol": "T",     "name": "time of flight",              "units": ["s"],         "dimension": "T"},
            {"symbol": "g",     "name": "acceleration due to gravity", "units": ["m/s²"],      "dimension": "LT^-2"},
        ],
        "canonical_formulas": [
            {"id": "time_of_flight",   "formula": "T = 2*u*sin(theta)/g",          "latex": "T = \\frac{2u\\sin\\theta}{g}"},
            {"id": "max_height",       "formula": "H = u^2*sin^2(theta)/(2*g)",    "latex": "H = \\frac{u^2\\sin^2\\theta}{2g}"},
            {"id": "horizontal_range", "formula": "R = u^2*sin(2*theta)/g",        "latex": "R = \\frac{u^2\\sin(2\\theta)}{g}"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "9.8 m/s", "intended": "9.8 m/s²", "entity": "gravity"},
            {"corrupt": "3O deg",  "intended": "30 deg",   "entity": "angle"},
        ],
        "exam_traps": [
            "NEET: Range is maximum at 45°: R_max = u²/g.",
            "NEET: Complementary angles θ and (90°−θ) give the same horizontal range.",
            "NEET: At maximum height, vertical velocity = 0; horizontal velocity = u·cos θ (unchanged).",
        ],
        "related": ["neet.phy.11.keph102"],
    },
    "keph104": {
        "target_quantities": [
            {"symbol": "F",  "name": "force",                     "units": ["N", "kN"],    "dimension": "MLT^-2"},
            {"symbol": "m",  "name": "mass",                      "units": ["kg", "g"],    "dimension": "M"},
            {"symbol": "a",  "name": "acceleration",              "units": ["m/s²"],       "dimension": "LT^-2"},
            {"symbol": "p",  "name": "linear momentum",           "units": ["kg m/s"],     "dimension": "MLT^-1"},
            {"symbol": "mu", "name": "coefficient of friction",   "units": [""],           "dimension": "1"},
        ],
        "canonical_formulas": [
            {"id": "newton_second_law", "formula": "F = m * a",                  "latex": "F = ma"},
            {"id": "momentum",          "formula": "p = m * v",                  "latex": "p = mv"},
            {"id": "impulse",           "formula": "J = F * delta_t = delta_p",  "latex": "J = F\\Delta t = \\Delta p"},
            {"id": "friction",          "formula": "f_max = mu * N",             "latex": "f_{max} = \\mu N"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "l500kg", "intended": "1500 kg", "entity": "mass"},
            {"corrupt": "isn",    "intended": "15N",     "entity": "force"},
        ],
        "exam_traps": [
            "NEET: Apparent weight in elevator: W_app = m(g+a) going up, m(g−a) going down.",
            "NEET: Static friction adjusts itself up to f_s = μ_s·N (self-adjusting).",
            "NEET: Pseudo-force acts in non-inertial frames only.",
        ],
        "related": ["neet.phy.11.keph102", "neet.phy.11.keph105"],
    },
    "keph105": {
        "target_quantities": [
            {"symbol": "W", "name": "work done",       "units": ["J", "kJ"],    "dimension": "ML^2T^-2"},
            {"symbol": "K", "name": "kinetic energy",  "units": ["J"],          "dimension": "ML^2T^-2"},
            {"symbol": "p", "name": "linear momentum", "units": ["kg m/s"],     "dimension": "MLT^-1"},
            {"symbol": "U", "name": "potential energy","units": ["J"],          "dimension": "ML^2T^-2"},
            {"symbol": "P", "name": "power",           "units": ["W", "kW"],    "dimension": "ML^2T^-3"},
        ],
        "canonical_formulas": [
            {"id": "work",                "formula": "W = F * d * cos(theta)",       "latex": "W = \\vec{F}\\cdot\\vec{d}"},
            {"id": "kinetic_energy",      "formula": "K = 0.5*m*v^2 = p^2/(2*m)",   "latex": "K = \\frac{1}{2}mv^2 = \\frac{p^2}{2m}"},
            {"id": "momentum_from_kinetic","formula": "p = sqrt(2*m*K)",             "latex": "p = \\sqrt{2mK}"},
            {"id": "work_energy_theorem", "formula": "W_net = K_f - K_i",           "latex": "W_{net} = K_f - K_i"},
            {"id": "power",               "formula": "P = W/t = F*v",               "latex": "P = \\frac{W}{t} = \\vec{F}\\cdot\\vec{v}"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "l00J", "intended": "100 J", "entity": "energy"},
            {"corrupt": "skW",  "intended": "5 kW",  "entity": "power"},
        ],
        "exam_traps": [
            "NEET: Work done by centripetal force is always ZERO (force ⊥ velocity).",
            "NEET: Equal KE → momentum ratio p1/p2 = √(m1/m2).",
            "NEET: Elastic collision conserves both KE and momentum; inelastic conserves only momentum.",
        ],
        "related": ["neet.phy.11.keph104", "neet.phy.11.keph106"],
    },
    "keph106": {
        "target_quantities": [
            {"symbol": "tau",   "name": "torque",              "units": ["N m"],       "dimension": "ML^2T^-2"},
            {"symbol": "L",     "name": "angular momentum",    "units": ["kg m^2/s"],  "dimension": "ML^2T^-1"},
            {"symbol": "I",     "name": "moment of inertia",   "units": ["kg m^2"],    "dimension": "ML^2"},
            {"symbol": "omega", "name": "angular velocity",    "units": ["rad/s"],     "dimension": "T^-1"},
            {"symbol": "alpha", "name": "angular acceleration","units": ["rad/s²"],    "dimension": "T^-2"},
        ],
        "canonical_formulas": [
            {"id": "torque_relation",  "formula": "tau = I * alpha",              "latex": "\\tau = I\\alpha"},
            {"id": "angular_momentum", "formula": "L = I * omega = r * p",        "latex": "L = I\\omega = \\vec{r}\\times\\vec{p}"},
            {"id": "rotational_ke",    "formula": "K_rot = 0.5*I*omega^2 = L^2/(2*I)", "latex": "K_{rot}=\\frac{1}{2}I\\omega^2=\\frac{L^2}{2I}"},
            {"id": "parallel_axis",    "formula": "I = I_cm + m * d^2",           "latex": "I = I_{cm} + md^2"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "rad/s2", "intended": "rad/s²", "entity": "angular_acceleration"},
        ],
        "exam_traps": [
            "NEET: No external torque → angular momentum conserved (I₁ω₁ = I₂ω₂).",
            "NEET: Torque dimensions [ML²T⁻²] = energy dimensions, but torque is a vector.",
            "NEET: Moment of inertia of ring = MR², disk = MR²/2, solid sphere = 2MR²/5.",
        ],
        "related": ["neet.phy.11.keph105", "neet.phy.11.keph107"],
    },
    "keph107": {
        "target_quantities": [
            {"symbol": "g",   "name": "acceleration due to gravity",        "units": ["m/s²"],          "dimension": "LT^-2"},
            {"symbol": "G",   "name": "universal gravitational constant",   "units": ["N m^2/kg^2"],    "dimension": "M^-1L^3T^-2"},
            {"symbol": "M",   "name": "mass of earth / body",               "units": ["kg"],            "dimension": "M"},
            {"symbol": "R",   "name": "radius of earth / orbit",            "units": ["m", "km"],       "dimension": "L"},
            {"symbol": "v_e", "name": "escape velocity",                    "units": ["m/s", "km/s"],   "dimension": "LT^-1"},
            {"symbol": "v_o", "name": "orbital velocity",                   "units": ["m/s", "km/s"],   "dimension": "LT^-1"},
        ],
        "canonical_formulas": [
            {"id": "gravity_surface",      "formula": "g = G*M/(R^2)",                          "latex": "g = \\frac{GM}{R^2}"},
            {"id": "radius_halved_gravity","formula": "g' = G*M/((R/2)^2) = 4*g",              "latex": "g' = \\frac{GM}{(R/2)^2} = 4g"},
            {"id": "escape_velocity",      "formula": "v_e = sqrt(2*G*M/R) = sqrt(2*g*R)",     "latex": "v_e = \\sqrt{\\frac{2GM}{R}} = \\sqrt{2gR}"},
            {"id": "orbital_velocity",     "formula": "v_o = sqrt(G*M/R) = sqrt(g*R)",         "latex": "v_o = \\sqrt{\\frac{GM}{R}} = \\sqrt{gR}"},
            {"id": "kepler_third_law",     "formula": "T^2 = 4*pi^2*r^3/(G*M)",               "latex": "T^2 \\propto r^3"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "11.2 km", "intended": "11.2 km/s", "entity": "escape_velocity"},
            {"corrupt": "9.8 m/s", "intended": "9.8 m/s²",  "entity": "gravity"},
        ],
        "exam_traps": [
            "NEET: Diameter halved (mass constant) → g' = 4g (quadruples).",
            "NEET: Escape velocity is independent of mass of the projected body.",
            "NEET: g = 0 at Earth's centre; g decreases both above and below the surface.",
        ],
        "related": ["neet.phy.11.keph101", "neet.phy.11.keph106"],
    },
    # Class 12 Physics high-yield
    "leph101": {
        "target_quantities": [
            {"symbol": "E",    "name": "electric field",     "units": ["N/C", "V/m"],   "dimension": "MLT^-3A^-1"},
            {"symbol": "q",    "name": "charge",             "units": ["C"],            "dimension": "AT"},
            {"symbol": "k",    "name": "Coulomb constant",   "units": ["N m^2/C^2"],    "dimension": "ML^3T^-4A^-2"},
            {"symbol": "Phi",  "name": "electric flux",      "units": ["N m^2/C"],      "dimension": "ML^3T^-3A^-1"},
        ],
        "canonical_formulas": [
            {"id": "coulombs_law",    "formula": "F = k*q1*q2/r^2",           "latex": "F = \\frac{kq_1q_2}{r^2}"},
            {"id": "electric_field",  "formula": "E = F/q = k*Q/r^2",         "latex": "E = \\frac{F}{q} = \\frac{kQ}{r^2}"},
            {"id": "gauss_law",       "formula": "Phi = q_enc / epsilon_0",    "latex": "\\Phi = \\frac{q_{enc}}{\\epsilon_0}"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "9x10^9", "intended": "9×10⁹ N m²/C²", "entity": "coulomb_constant"},
        ],
        "exam_traps": [
            "NEET: Electric field inside a conductor = 0 in static equilibrium.",
            "NEET: Gauss's law: total flux = q_enc/ε₀ regardless of charge distribution outside the surface.",
            "NEET: Field due to infinite line charge E = λ/(2πε₀r) — varies as 1/r not 1/r².",
        ],
        "related": ["neet.phy.12.leph102"],
    },
    "leph102": {
        "target_quantities": [
            {"symbol": "V",  "name": "electric potential",  "units": ["V"],         "dimension": "ML^2T^-3A^-1"},
            {"symbol": "C",  "name": "capacitance",         "units": ["F", "μF"],  "dimension": "M^-1L^-2T^4A^2"},
            {"symbol": "U",  "name": "energy stored",       "units": ["J"],         "dimension": "ML^2T^-2"},
        ],
        "canonical_formulas": [
            {"id": "potential",          "formula": "V = k*Q/r",                   "latex": "V = \\frac{kQ}{r}"},
            {"id": "capacitance",        "formula": "C = Q/V = epsilon_0*A/d",     "latex": "C = \\frac{Q}{V} = \\frac{\\epsilon_0 A}{d}"},
            {"id": "energy_stored",      "formula": "U = 0.5*C*V^2 = Q^2/(2*C)",  "latex": "U = \\frac{1}{2}CV^2 = \\frac{Q^2}{2C}"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Capacitors in series: 1/C_eff = Σ(1/Cᵢ); in parallel: C_eff = ΣCᵢ.",
            "NEET: Dielectric inserted with battery connected → V constant, charge increases.",
            "NEET: Dielectric inserted after disconnecting battery → Q constant, V decreases.",
        ],
        "related": ["neet.phy.12.leph101", "neet.phy.12.leph103"],
    },
    "leph103": {
        "target_quantities": [
            {"symbol": "I",   "name": "current",             "units": ["A"],         "dimension": "A"},
            {"symbol": "R",   "name": "resistance",          "units": ["Ω"],         "dimension": "ML^2T^-3A^-2"},
            {"symbol": "rho", "name": "resistivity",         "units": ["Ω m"],       "dimension": "ML^3T^-3A^-2"},
            {"symbol": "EMF", "name": "electromotive force", "units": ["V"],         "dimension": "ML^2T^-3A^-1"},
        ],
        "canonical_formulas": [
            {"id": "ohms_law",        "formula": "V = I * R",              "latex": "V = IR"},
            {"id": "resistivity",     "formula": "R = rho * L / A",        "latex": "R = \\frac{\\rho L}{A}"},
            {"id": "kirchhoffs_kv",   "formula": "sum(V) = 0 in closed loop","latex": "\\sum V = 0"},
            {"id": "terminal_voltage","formula": "V = EMF - I * r",        "latex": "V = \\mathcal{E} - Ir"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "lOA", "intended": "10A", "entity": "current"},
        ],
        "exam_traps": [
            "NEET: Resistors in series: R_eff = ΣRᵢ; in parallel: 1/R_eff = Σ(1/Rᵢ).",
            "NEET: Wheatstone bridge balanced when P/Q = R/S (no current through galvanometer).",
        ],
        "related": ["neet.phy.12.leph104"],
    },
    "leph203": {
        "target_quantities": [
            {"symbol": "lambda", "name": "de Broglie wavelength", "units": ["m", "nm", "Å"], "dimension": "L"},
            {"symbol": "phi",    "name": "work function",          "units": ["eV", "J"],       "dimension": "ML^2T^-2"},
            {"symbol": "KE_max", "name": "maximum kinetic energy", "units": ["eV", "J"],       "dimension": "ML^2T^-2"},
        ],
        "canonical_formulas": [
            {"id": "photoelectric",  "formula": "KE_max = h*nu - phi",    "latex": "KE_{max} = h\\nu - \\phi"},
            {"id": "de_broglie",     "formula": "lambda = h / p",         "latex": "\\lambda = \\frac{h}{p}"},
            {"id": "stopping_pot",   "formula": "eV_s = KE_max",          "latex": "eV_s = KE_{max}"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Photoelectric effect proves particle nature of light.",
            "NEET: Stopping potential depends on frequency, NOT intensity.",
            "NEET: de Broglie wavelength λ = h/p; for electron accelerated through V: λ = h/√(2meV).",
        ],
        "related": ["neet.phy.12.leph204", "neet.phy.11.keph101"],
    },
}

# ---------------------------------------------------------------------------
# High-yield domain data: Chemistry (NEET focus)
# ---------------------------------------------------------------------------

CHEMISTRY_HIGH_YIELD = {
    "kech101": {
        "target_quantities": [
            {"symbol": "n",      "name": "moles",              "units": ["mol"],           "dimension": "N (amount of substance)"},
            {"symbol": "M",      "name": "molar mass",         "units": ["g/mol"],         "dimension": "MN^-1"},
            {"symbol": "N_A",    "name": "Avogadro number",    "units": ["mol^-1"],        "dimension": "N^-1"},
        ],
        "canonical_formulas": [
            {"id": "moles_from_mass",    "formula": "n = mass / molar_mass",               "latex": "n = \\frac{m}{M}"},
            {"id": "moles_from_volume",  "formula": "n = V / 22.4  (at STP)",              "latex": "n = \\frac{V}{22.4}"},
            {"id": "empirical_formula",  "formula": "% element / atomic mass = mole ratio","latex": "\\text{ratio} = \\frac{\\% \\text{element}}{A_r}"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "22.4L",  "intended": "22.4 L",  "entity": "molar_volume"},
        ],
        "exam_traps": [
            "NEET: Molar volume = 22.4 L at STP (0°C, 1 atm); 24.5 L at NTP (25°C, 1 bar).",
            "NEET: Empirical formula gives simplest ratio; molecular formula = n × empirical formula.",
            "NEET: Normality = n × Molarity (n = valency factor).",
        ],
        "related": ["neet.che.11.kech105"],
    },
    "kech102": {
        "target_quantities": [
            {"symbol": "Z",   "name": "atomic number",        "units": [],         "dimension": ""},
            {"symbol": "n",   "name": "principal quantum number","units": [],       "dimension": ""},
            {"symbol": "E_n", "name": "Bohr energy level",    "units": ["eV", "J"],"dimension": "ML^2T^-2"},
        ],
        "canonical_formulas": [
            {"id": "bohr_energy",    "formula": "E_n = -13.6 * Z^2 / n^2  eV",   "latex": "E_n = -\\frac{13.6 Z^2}{n^2}\\text{ eV}"},
            {"id": "bohr_radius",    "formula": "r_n = a_0 * n^2 / Z",            "latex": "r_n = \\frac{a_0 n^2}{Z}"},
            {"id": "rydberg",        "formula": "1/lambda = R * Z^2 * (1/n1^2 - 1/n2^2)", "latex": "\\frac{1}{\\lambda}=RZ^2\\left(\\frac{1}{n_1^2}-\\frac{1}{n_2^2}\\right)"},
        ],
        "common_ocr_confusions": [
            {"corrupt": "13.6eV", "intended": "13.6 eV", "entity": "ionization_energy"},
        ],
        "exam_traps": [
            "NEET: Ionization energy of H = 13.6 eV (remove electron from n=1).",
            "NEET: Lyman series → UV; Balmer → visible; Paschen → IR.",
            "NEET: Heisenberg: Δx·Δp ≥ h/(4π) — cannot simultaneously know exact position and momentum.",
        ],
        "related": ["neet.che.11.kech103", "neet.phy.12.leph203"],
    },
    "kech105": {
        "target_quantities": [
            {"symbol": "H",     "name": "enthalpy",                "units": ["kJ/mol"],   "dimension": "ML^2T^-2N^-1"},
            {"symbol": "S",     "name": "entropy",                 "units": ["J/mol K"],  "dimension": "ML^2T^-2N^-1K^-1"},
            {"symbol": "G",     "name": "Gibbs free energy",       "units": ["kJ/mol"],   "dimension": "ML^2T^-2N^-1"},
        ],
        "canonical_formulas": [
            {"id": "gibbs",         "formula": "G = H - T*S",                   "latex": "G = H - TS"},
            {"id": "spontaneous",   "formula": "delta_G < 0  => spontaneous",   "latex": "\\Delta G < 0 \\text{ (spontaneous)}"},
            {"id": "hess_law",      "formula": "delta_H_rxn = sum(delta_Hf_products) - sum(delta_Hf_reactants)", "latex": "\\Delta H_{rxn} = \\sum\\Delta H_f^{\\circ}(P) - \\sum\\Delta H_f^{\\circ}(R)"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: ΔG < 0 → spontaneous; ΔG > 0 → non-spontaneous; ΔG = 0 → equilibrium.",
            "NEET: Exothermic: ΔH < 0; Endothermic: ΔH > 0.",
            "NEET: Bond dissociation energy always positive (energy absorbed to break bonds).",
        ],
        "related": ["neet.che.11.kech106"],
    },
    "kech106": {
        "target_quantities": [
            {"symbol": "K_c",  "name": "equilibrium constant (conc)", "units": ["varies"],  "dimension": "varies"},
            {"symbol": "K_a",  "name": "acid dissociation constant",  "units": ["mol/L"],   "dimension": ""},
            {"symbol": "pH",   "name": "pH",                          "units": [],           "dimension": ""},
        ],
        "canonical_formulas": [
            {"id": "equilibrium_expr",  "formula": "K_c = [products]^coeff / [reactants]^coeff", "latex": "K_c = \\frac{[\\text{products}]^p}{[\\text{reactants}]^r}"},
            {"id": "pH",                "formula": "pH = -log[H+]",                               "latex": "\\text{pH} = -\\log[H^+]"},
            {"id": "kw",                "formula": "K_w = [H+][OH-] = 1e-14 at 25C",              "latex": "K_w = [H^+][OH^-] = 10^{-14}"},
            {"id": "henderson",         "formula": "pH = pKa + log([A-]/[HA])",                   "latex": "\\text{pH} = pK_a + \\log\\frac{[A^-]}{[HA]}"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Le Chatelier's principle — system opposes change to restore equilibrium.",
            "NEET: Adding catalyst does NOT shift equilibrium; only speeds up reaching it.",
            "NEET: pH of pure water = 7 at 25°C; < 7 as temperature rises (K_w increases).",
        ],
        "related": ["neet.che.11.kech105", "neet.che.12.lech102"],
    },
    "lech102": {
        "target_quantities": [
            {"symbol": "E",     "name": "cell potential (EMF)",   "units": ["V"],         "dimension": "ML^2T^-3A^-1"},
            {"symbol": "G",     "name": "Gibbs free energy",      "units": ["kJ/mol"],    "dimension": "ML^2T^-2N^-1"},
            {"symbol": "n",     "name": "moles of electrons",     "units": ["mol"],       "dimension": "N"},
        ],
        "canonical_formulas": [
            {"id": "cell_potential", "formula": "E_cell = E_cathode - E_anode",        "latex": "E_{cell} = E_{cathode} - E_{anode}"},
            {"id": "gibbs_cell",     "formula": "delta_G = -n * F * E_cell",           "latex": "\\Delta G = -nFE_{cell}"},
            {"id": "nernst",         "formula": "E = E0 - (RT/nF)*ln(Q)",             "latex": "E = E^\\circ - \\frac{RT}{nF}\\ln Q"},
            {"id": "faraday",        "formula": "W = (M * I * t) / (n * F)",          "latex": "W = \\frac{MIt}{nF}"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: E_cell > 0 → spontaneous reaction; ΔG < 0.",
            "NEET: SHE (standard hydrogen electrode) potential = 0.00 V by convention.",
            "NEET: 1 Faraday = 96500 C/mol electrons.",
        ],
        "related": ["neet.che.11.kech106", "neet.che.12.lech103"],
    },
    "lech103": {
        "target_quantities": [
            {"symbol": "k",    "name": "rate constant",           "units": ["varies"],    "dimension": ""},
            {"symbol": "t_half","name": "half-life",              "units": ["s", "min"],  "dimension": "T"},
        ],
        "canonical_formulas": [
            {"id": "first_order_rate",  "formula": "rate = k * [A]",                              "latex": "r = k[A]"},
            {"id": "first_order_k",     "formula": "k = 2.303/t * log([A]0/[A])",                "latex": "k = \\frac{2.303}{t}\\log\\frac{[A]_0}{[A]}"},
            {"id": "half_life_1st",     "formula": "t_half = 0.693 / k",                          "latex": "t_{1/2} = \\frac{0.693}{k}"},
            {"id": "arrhenius",         "formula": "k = A * exp(-Ea/(R*T))",                      "latex": "k = Ae^{-E_a/RT}"},
        ],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: First-order half-life is INDEPENDENT of initial concentration.",
            "NEET: Zero-order half-life = [A]₀/(2k) — does depend on initial concentration.",
            "NEET: Rate of reaction ≠ rate constant; rate depends on concentration; k depends only on temperature.",
        ],
        "related": ["neet.che.12.lech102"],
    },
}

# ---------------------------------------------------------------------------
# High-yield domain data: Biology (NEET focus)
# ---------------------------------------------------------------------------

BIOLOGY_HIGH_YIELD = {
    "kebo108": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Prokaryotes have 70S ribosomes (50S + 30S); eukaryotes have 80S (60S + 40S).",
            "NEET: Mitochondria and chloroplasts have their own DNA and 70S ribosomes.",
            "NEET: Cell wall in plants = cellulose; fungi = chitin; bacteria = peptidoglycan.",
            "NEET: Lysosome = 'suicide bag' (hydrolytic enzymes); Golgi = 'traffic police' for secretion.",
        ],
        "related": ["neet.bio.11.kebo109", "neet.bio.11.kebo110"],
    },
    "kebo110": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Mitosis produces 2 identical diploid cells; meiosis produces 4 haploid cells.",
            "NEET: Crossing over occurs in Prophase I (pachytene) of meiosis — increases genetic diversity.",
            "NEET: S phase = DNA replication; G2 = preparation for division; G0 = quiescent.",
        ],
        "related": ["neet.bio.11.kebo108", "neet.bio.12.lebo104"],
    },
    "kebo111": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: PS II absorbs 680 nm (P680); PS I absorbs 700 nm (P700).",
            "NEET: O₂ is released in light reaction (photolysis of water), NOT Calvin cycle.",
            "NEET: C4 plants (maize, sugarcane) fix CO₂ via PEP carboxylase in mesophyll; CO₂ is then released to bundle sheath for Calvin cycle.",
            "NEET: CAM plants fix CO₂ at night (open stomata); Calvin cycle runs during day.",
        ],
        "related": ["neet.bio.11.kebo112"],
    },
    "kebo112": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Glycolysis occurs in cytoplasm; Krebs cycle in mitochondrial matrix; ETC on inner mitochondrial membrane.",
            "NEET: Net ATP from 1 glucose (aerobic): ~36–38 ATP; anaerobic: only 2 ATP.",
            "NEET: RQ = CO₂ released / O₂ consumed; RQ = 1 for carbohydrates, < 1 for fats, > 1 for organic acids.",
        ],
        "related": ["neet.bio.11.kebo111", "neet.bio.11.kebo109"],
    },
    "lebo104": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Mendel's law of segregation: alleles separate during gamete formation.",
            "NEET: Incomplete dominance: F1 is intermediate (e.g., red × white → pink).",
            "NEET: Codominance: both alleles expressed (e.g., AB blood group).",
            "NEET: Sex-linked traits on X chromosome; colour blindness and haemophilia are X-linked recessive.",
        ],
        "related": ["neet.bio.12.lebo105"],
    },
    "lebo105": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: DNA replication is semi-conservative (proved by Meselson-Stahl experiment).",
            "NEET: Template strand is read 3'→5'; mRNA is synthesised 5'→3'.",
            "NEET: tRNA carries amino acids; rRNA is structural component of ribosome; mRNA is the message.",
            "NEET: lac operon is inducible (induced by lactose); trp operon is repressible.",
        ],
        "related": ["neet.bio.12.lebo104", "neet.bio.12.lebo109"],
    },
    "lebo107": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: Active immunity = antibodies made by body (slow, long-lasting); passive = antibodies given (fast, short).",
            "NEET: B-lymphocytes → humoral immunity (antibodies); T-lymphocytes → cell-mediated immunity.",
            "NEET: HIV infects helper T-cells (CD4+), reducing immunity.",
            "NEET: Cancer: oncogenes promote cell division; tumour suppressor genes inhibit it.",
        ],
        "related": ["neet.bio.12.lebo108"],
    },
    "kebo115": {
        "target_quantities": [],
        "canonical_formulas": [],
        "common_ocr_confusions": [],
        "exam_traps": [
            "NEET: SA node (pacemaker) generates cardiac impulse; normal heart rate ~72 bpm.",
            "NEET: Cardiac output = heart rate × stroke volume.",
            "NEET: ABO blood groups: A (antigen A, antibody b), B (B, a), AB (A+B, none), O (none, a+b).",
            "NEET: Rh factor — Rh+ has Rh antigen; erythroblastosis fetalis when Rh− mother carries Rh+ child (2nd pregnancy).",
        ],
        "related": ["neet.bio.11.kebo114", "neet.bio.11.kebo116"],
    },
}

# ---------------------------------------------------------------------------
# OKF Tree: explicit parent→child edges (curriculum graph)
# ---------------------------------------------------------------------------

# Root nodes: neet.root → subjects → class groups → chapters
OKF_TREE_EDGES = [
    # Subject roots under NEET root
    ("neet.root", "neet.phy",  "child"),
    ("neet.root", "neet.che",  "child"),
    ("neet.root", "neet.bio",  "child"),
    # Physics class groups
    ("neet.phy",  "neet.phy.11", "child"),
    ("neet.phy",  "neet.phy.12", "child"),
    # Chemistry class groups
    ("neet.che",  "neet.che.11", "child"),
    ("neet.che",  "neet.che.12", "child"),
    # Biology class groups
    ("neet.bio",  "neet.bio.11", "child"),
    ("neet.bio",  "neet.bio.12", "child"),
]

# Chapter-level edges are added dynamically in main()


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

MAX_PDF_SIZE_BYTES = 15 * 1024 * 1024  # Skip summary extraction for PDFs > 15 MB


def extract_ncert_summary(pdf_path):
    """Extract the summary/points-to-ponder section from the end of an NCERT chapter PDF.
    Skips files larger than MAX_PDF_SIZE_BYTES to avoid slow parsing of large bio books.
    """
    try:
        if os.path.getsize(pdf_path) > MAX_PDF_SIZE_BYTES:
            return ""  # Too large; rely on hardcoded high-yield data instead
        reader = pypdf.PdfReader(pdf_path)
        num_pages = len(reader.pages)
        start_page = max(0, num_pages - 5)
        tail_text = "\n".join([reader.pages[i].extract_text() or "" for i in range(start_page, num_pages)])
        m = re.search(r'(SUMMARY|POINTS TO PONDER|SUMMARY\s*\n)(.*?)(EXERCISES|Exercises|ANSWERS|$)', tail_text, re.DOTALL | re.IGNORECASE)
        if m:
            return m.group(2).strip()[:2000]
        return ""
    except Exception:
        return ""


def parse_neet_pyqs(pyq_pdf_path, max_q=5):
    """Extract sample questions from NEET PYQ PDFs."""
    questions = []
    try:
        reader = pypdf.PdfReader(pyq_pdf_path)
        all_text = ""
        for p in reader.pages[:6]:
            t = p.extract_text()
            if t:
                all_text += "\n" + t

        q_blocks = re.split(r'\n\s*Q(\d+)\.\s*', all_text)
        if len(q_blocks) > 1:
            for i in range(1, len(q_blocks), 2):
                q_num = q_blocks[i]
                q_text = q_blocks[i + 1].strip() if i + 1 < len(q_blocks) else ""
                lines = q_text.split('\n')
                prompt = lines[0] if lines else q_text[:200]
                questions.append({"q_num": q_num, "text": q_text[:500], "prompt": prompt})
                if len(questions) >= max_q:
                    break
    except Exception:
        pass
    return questions


def build_node_id(subject, class_num, slug):
    """Build canonical OKF node ID: neet.<subject>.<class_num>.<slug>"""
    return f"neet.{subject}.{class_num}.{slug}"


def generate_okf_card(node_id, subject, meta, high_yield, summary_text, pyqs,
                      parent_id=None, children=None, related=None):
    """Generate a single Open Knowledge Format (.okf.md) card with proper tree metadata."""
    exam_tags = ["NEET"]
    # Add JEE tag only for Physics chapters that overlap JEE Main syllabus
    if subject == "phy":
        exam_tags.append("JEE_Main")

    yaml_dict = {
        "id": node_id,
        "node_type": "chapter",
        "parent_id": parent_id or f"neet.{subject}.{meta['class']}",
        "children": children or [],
        "related": related or high_yield.get("related", []),
        "domain": subject,
        "subject": {"phy": "physics", "che": "chemistry", "bio": "biology"}.get(subject, subject),
        "class": meta["class"],
        "chapter_num": meta["ch"],
        "chapter_title": meta["title"],
        "unit": meta["unit"],
        "exam_tags": exam_tags,
        "target_quantities": high_yield.get("target_quantities", []),
        "canonical_formulas": high_yield.get("canonical_formulas", []),
        "common_ocr_confusions": high_yield.get("common_ocr_confusions", []),
        "exam_traps": high_yield.get("exam_traps", []),
    }

    subject_label = {"phy": "Physics", "che": "Chemistry", "bio": "Biology"}.get(subject, subject.title())
    yaml_header = yaml.dump(yaml_dict, sort_keys=False, allow_unicode=True)

    md_content = f"""---
{yaml_header}---

# {meta['title']} ({meta['unit']})
> **Standard:** Class {meta['class']} NCERT {subject_label} | **Primary Target: NEET**{' | Also: JEE Main' if 'JEE_Main' in exam_tags else ''}

## 1. Key Principles & Conceptual Summary
{summary_text if summary_text else "Core conceptual framework based on NCERT guidelines for " + meta['title'] + "."}

## 2. Canonical Formulas & Constraints
"""
    for f in high_yield.get("canonical_formulas", []):
        md_content += f"- **{f.get('id', 'formula')}**: `${f.get('latex', f.get('formula', ''))}$`\n"

    if high_yield.get("exam_traps"):
        md_content += "\n## 3. High-Yield NEET Exam Traps\n"
        for trap in high_yield["exam_traps"]:
            md_content += f"- ⚠️ {trap}\n"

    if related or high_yield.get("related"):
        rel_ids = related or high_yield.get("related", [])
        if rel_ids:
            md_content += "\n## 4. Related Nodes (OKF Graph Links)\n"
            for r in rel_ids:
                md_content += f"- [{r}]({r})\n"

    if pyqs:
        md_content += "\n## 5. NEET Previous Year Questions (PYQs)\n"
        for q in pyqs:
            md_content += f"### PYQ Q{q['q_num']}\n```text\n{q['text']}\n```\n"

    return yaml_dict, md_content


# ---------------------------------------------------------------------------
# Main compiler
# ---------------------------------------------------------------------------

def main():
    print("=" * 60)
    print("EduPulse: Building NEET-focused OKF Knowledge Base")
    print("=" * 60)

    os.makedirs(CARDS_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    # Clean existing cards so old formats/IDs do not linger
    for old_card in glob.glob(os.path.join(CARDS_DIR, "*.okf.md")):
        try:
            os.remove(old_card)
        except Exception:
            pass

    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # --- Schema ---
    cursor.execute("""
    CREATE TABLE okf_nodes (
        id TEXT PRIMARY KEY,
        subject TEXT,
        class_level INTEGER,
        chapter_title TEXT,
        unit_name TEXT,
        frontmatter_yaml TEXT,
        body_markdown TEXT,
        keywords TEXT,
        node_type TEXT DEFAULT 'chapter',
        parent_id TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE okf_edges (
        from_id TEXT,
        to_id TEXT,
        edge_type TEXT,
        PRIMARY KEY (from_id, to_id, edge_type)
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
    CREATE TABLE okf_quantities (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        node_id TEXT,
        symbol TEXT,
        name TEXT,
        units TEXT,
        dimension TEXT,
        FOREIGN KEY (node_id) REFERENCES okf_nodes(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE okf_exam_traps (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        node_id TEXT,
        trap_text TEXT,
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

    cursor.execute("CREATE INDEX idx_okf_keywords ON okf_nodes(keywords);")
    cursor.execute("CREATE INDEX idx_okf_subject  ON okf_nodes(subject);")
    cursor.execute("CREATE INDEX idx_okf_edges_from ON okf_edges(from_id);")
    cursor.execute("CREATE INDEX idx_okf_edges_to   ON okf_edges(to_id);")

    # Insert static tree edges
    for (from_id, to_id, etype) in OKF_TREE_EDGES:
        cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)", (from_id, to_id, etype))
        cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)", (to_id, from_id, "parent"))

    # Load NEET PYQs
    neet_pyqs_phy = []
    neet_pyqs_che = []
    neet_pyqs_bio = []
    for year in ["2024", "2025"]:
        pyq_dir = os.path.join(DATA_DIR, "neet_pyq", year)
        if os.path.isdir(pyq_dir):
            for fname in sorted(os.listdir(pyq_dir)):
                if fname.endswith(".pdf"):
                    fpath = os.path.join(pyq_dir, fname)
                    qs = parse_neet_pyqs(fpath, max_q=3)
                    if qs:
                        neet_pyqs_phy.extend(qs[:1])
                        print(f"  [PYQ] Extracted {len(qs)} questions from NEET {year}/{fname}")
                        break

    total_cards = 0

    # -----------------------------------------------------------------------
    # Process each subject catalogue
    # -----------------------------------------------------------------------
    catalogues = [
        ("phy", PHYSICS_11),
        ("phy", PHYSICS_12),
        ("che", CHEMISTRY_11),
        ("che", CHEMISTRY_12),
        ("bio", BIOLOGY_11),
        ("bio", BIOLOGY_12),
    ]

    high_yield_maps = {
        "phy": PHYSICS_HIGH_YIELD,
        "che": CHEMISTRY_HIGH_YIELD,
        "bio": BIOLOGY_HIGH_YIELD,
    }

    for (subject, catalogue) in catalogues:
        class_num = list(catalogue.values())[0]["class"]
        print(f"\n[*] Processing Class {class_num} {subject.upper()} chapters ({len(catalogue)} chapters)...")

        for slug, meta in catalogue.items():
            pdf_path = os.path.join(DATA_DIR, meta["folder"], f"{slug}.pdf")
            summary = extract_ncert_summary(pdf_path) if os.path.exists(pdf_path) else ""

            high_yield_map = high_yield_maps[subject]
            high_yield = high_yield_map.get(slug, {
                "target_quantities": [], "canonical_formulas": [],
                "common_ocr_confusions": [], "exam_traps": [],
            })

            node_id = build_node_id(subject, class_num, slug)
            parent_id = f"neet.{subject}.{class_num}"
            related = high_yield.get("related", [])

            # Attach NEET PYQs to first physics chapter with high-yield data
            pyqs_for_card = []
            if subject == "phy" and slug == "keph102" and neet_pyqs_phy:
                pyqs_for_card = neet_pyqs_phy

            yaml_dict, md_content = generate_okf_card(
                node_id, subject, meta, high_yield, summary, pyqs_for_card,
                parent_id=parent_id, related=related
            )

            card_file = os.path.join(CARDS_DIR, f"{node_id}.okf.md")
            with open(card_file, "w", encoding="utf-8") as fh:
                fh.write(md_content)

            # Build keyword index
            subject_full = {"phy": "physics", "che": "chemistry", "bio": "biology"}.get(subject, subject)
            keywords_list = [
                meta["title"], meta["unit"], subject_full,
                f"class {meta['class']}", "NEET",
                slug,
            ]
            for q in high_yield.get("target_quantities", []):
                keywords_list.append(q.get("name", ""))
                keywords_list.append(q.get("symbol", ""))
                keywords_list.extend(q.get("units", []))
            for formula in high_yield.get("canonical_formulas", []):
                keywords_list.append(formula.get("formula", ""))
            for trap in high_yield.get("exam_traps", []):
                keywords_list.extend(trap.split()[:8])
            keywords_str = " ".join(filter(None, set(keywords_list)))

            cursor.execute(
                "INSERT INTO okf_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (node_id, subject_full, meta["class"], meta["title"],
                 meta["unit"], yaml.dump(yaml_dict), md_content, keywords_str,
                 "chapter", parent_id)
            )

            # Chapter edge: class_group → chapter
            cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)",
                           (parent_id, node_id, "child"))
            cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)",
                           (node_id, parent_id, "parent"))

            # Related edges
            for rel_id in related:
                cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)",
                               (node_id, rel_id, "related"))
                cursor.execute("INSERT OR IGNORE INTO okf_edges VALUES (?, ?, ?)",
                               (rel_id, node_id, "related"))

            # Formulas
            for formula in high_yield.get("canonical_formulas", []):
                cursor.execute(
                    "INSERT INTO okf_formulas (node_id, formula_id, formula_latex, formula_code, constraints) VALUES (?, ?, ?, ?, ?)",
                    (node_id, formula.get("id"), formula.get("latex"),
                     formula.get("formula"), json.dumps(formula.get("constraints", [])))
                )

            # Quantities
            for q in high_yield.get("target_quantities", []):
                cursor.execute(
                    "INSERT INTO okf_quantities (node_id, symbol, name, units, dimension) VALUES (?, ?, ?, ?, ?)",
                    (node_id, q.get("symbol"), q.get("name"),
                     json.dumps(q.get("units", [])), q.get("dimension"))
                )

            # Exam traps
            for trap in high_yield.get("exam_traps", []):
                cursor.execute(
                    "INSERT INTO okf_exam_traps (node_id, trap_text) VALUES (?, ?)",
                    (node_id, trap)
                )

            # OCR confusions
            for ocr in high_yield.get("common_ocr_confusions", []):
                cursor.execute(
                    "INSERT OR REPLACE INTO ocr_confusions VALUES (?, ?, ?, ?)",
                    (ocr["corrupt"], ocr["intended"], ocr.get("entity", ""), node_id)
                )

            total_cards += 1
            has_data = "✓ high-yield" if high_yield.get("canonical_formulas") or high_yield.get("exam_traps") else "  (stub)"
            print(f"  [OK] {node_id}  {has_data}")

    # Insert NEET PYQs
    pyq_node_id = "neet.phy.11.keph102"
    for i, q in enumerate(neet_pyqs_phy):
        q_id = f"pyq.neet.phy.q{i+1}"
        cursor.execute(
            "INSERT OR REPLACE INTO pyq_questions VALUES (?, ?, ?, ?, ?, ?, ?)",
            (q_id, "NEET", 0, "NEET_PYQ", "physics", q["text"], pyq_node_id)
        )

    conn.commit()
    conn.close()

    shutil.copyfile(DB_PATH, ASSET_DB_PATH)

    db_size_kb = os.path.getsize(DB_PATH) / 1024
    asset_size_kb = os.path.getsize(ASSET_DB_PATH) / 1024

    print("\n" + "=" * 60)
    print("Compilation Complete!")
    print(f"[*] Total OKF Cards: {total_cards}")
    print(f"[*] Cards directory: {CARDS_DIR}")
    print(f"[*] SQLite DB: {DB_PATH} ({db_size_kb:.1f} KB)")
    print(f"[*] Android Asset: {ASSET_DB_PATH} ({asset_size_kb:.1f} KB)")
    print("=" * 60)


if __name__ == "__main__":
    main()
