---
id: neet.phy.12.leph103
node_type: chapter
parent_id: neet.phy.12
children: []
related:
- neet.phy.12.leph104
domain: phy
subject: physics
class: 12
chapter_num: 3
chapter_title: Current Electricity
unit: 'Unit II: Current Electricity'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: I
  name: current
  units:
  - A
  dimension: A
- symbol: R
  name: resistance
  units:
  - Ω
  dimension: ML^2T^-3A^-2
- symbol: rho
  name: resistivity
  units:
  - Ω m
  dimension: ML^3T^-3A^-2
- symbol: EMF
  name: electromotive force
  units:
  - V
  dimension: ML^2T^-3A^-1
canonical_formulas:
- id: ohms_law
  formula: V = I * R
  latex: V = IR
- id: resistivity
  formula: R = rho * L / A
  latex: R = \frac{\rho L}{A}
- id: kirchhoffs_kv
  formula: sum(V) = 0 in closed loop
  latex: \sum V = 0
- id: terminal_voltage
  formula: V = EMF - I * r
  latex: V = \mathcal{E} - Ir
common_ocr_confusions:
- corrupt: lOA
  intended: 10A
  entity: current
exam_traps:
- 'NEET: Resistors in series: R_eff = ΣRᵢ; in parallel: 1/R_eff = Σ(1/Rᵢ).'
- 'NEET: Wheatstone bridge balanced when P/Q = R/S (no current through galvanometer).'
---

# Current Electricity (Unit II: Current Electricity)
> **Standard:** Class 12 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. Current through a given area of a conductor is the net charge passing
per unit time through the area.
2. To maintain a steady current, we must have a closed circuit in which
an external agency moves electric charge from lower to higher potential
energy. The work done per unit charge by the source in taking the
charge from lower to higher potential energy (i.e., from one terminal
of the source to the other) is called the electromotive force, or emf, of
the source. Note that the emf is not a force; it is the voltage difference
between the two terminals of a source in open circuit.
3. Ohm’s law : The electric current I flowing through a substance is
proportional to the voltage V across its ends, i.e., V µ  I or V = RI,
where R is called the resistance of the substance. The unit of resistance
is ohm: 1W = 1 V A–1 .
Reprint 2025-26

Current
Electricity
103
4. The resistance  R of a conductor depends on its length l and
cross-sectional area A through the relation,
lR A
ρ=
where r, called resistivity is a property of the material and depends on
temperature and pressure.
5. Electrical resistivity of substances varies over a very wide range. Metals
have low resistivity, in the range of 10 –8  W m to 10–6  W m. Insulators
like glass and rubber have 10 22 to 10 24 times greater resistivity.
Semiconductors like Si and Ge lie roughly in the middle range of
resistivity on a logarithmic scale.
6. In most substances, the carriers of current are electrons; in some
cases, for example, ionic crystals and electrolytic liquids, positive and
negative ions carry the electric current.
7. Current density  j gives the amount of charge flowing per second per
unit area normal to the flow,
j = nq vd
where n is the number density (number per unit volume) of charge
carriers each of charge q, and vd is the drift velocity  of the charge
carriers. For electrons  q = – e. If j is normal to a cross-sectional area
A and is constant over the area, the magnitude of the current I through
the area is ne

## 2. Canonical Formulas & Constraints
- **ohms_law**: `$V = IR$`
- **resistivity**: `$R = \frac{\rho L}{A}$`
- **kirchhoffs_kv**: `$\sum V = 0$`
- **terminal_voltage**: `$V = \mathcal{E} - Ir$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Resistors in series: R_eff = ΣRᵢ; in parallel: 1/R_eff = Σ(1/Rᵢ).
- ⚠️ NEET: Wheatstone bridge balanced when P/Q = R/S (no current through galvanometer).

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.12.leph104](neet.phy.12.leph104)
