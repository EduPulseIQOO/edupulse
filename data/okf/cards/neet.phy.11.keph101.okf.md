---
id: neet.phy.11.keph101
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph106
- neet.phy.11.keph107
domain: phy
subject: physics
class: 11
chapter_num: 1
chapter_title: Units and Measurement
unit: 'Unit I: Physical World and Measurement'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: h
  name: Planck constant
  units:
  - J s
  - J*s
  dimension: ML^2T^-1
- symbol: L
  name: angular momentum
  units:
  - kg m^2/s
  - J s
  dimension: ML^2T^-1
- symbol: p
  name: linear momentum
  units:
  - kg m/s
  - N s
  dimension: MLT^-1
- symbol: tau
  name: moment of force / torque
  units:
  - N m
  dimension: ML^2T^-2
- symbol: W
  name: work / energy
  units:
  - J
  - N m
  dimension: ML^2T^-2
- symbol: G
  name: universal gravitational constant
  units:
  - N m^2/kg^2
  dimension: M^-1L^3T^-2
- symbol: eta
  name: coefficient of viscosity
  units:
  - Pa s
  - poise
  dimension: ML^-1T^-1
- symbol: sigma
  name: surface tension
  units:
  - N/m
  - J/m^2
  dimension: MT^-2
canonical_formulas:
- id: planck_energy
  formula: E = h * nu => [h] = ML^2T^-1
  latex: E = h\nu \implies [h] = [M L^2 T^{-1}]
- id: angular_momentum_def
  formula: L = r * p => [L] = ML^2T^-1
  latex: L = r \times p \implies [L] = [M L^2 T^{-1}]
- id: torque_def
  formula: tau = r * F => [tau] = ML^2T^-2
  latex: \tau = r \times F \implies [\tau] = [M L^2 T^{-2}]
- id: linear_momentum_def
  formula: p = m * v => [p] = MLT^-1
  latex: p = mv \implies [p] = [M L T^{-1}]
- id: gravitational_constant
  formula: G = F * r^2 / (m1 * m2) => [G] = M^-1L^3T^-2
  latex: G = \frac{F r^2}{m_1 m_2} \implies [G] = [M^{-1} L^3 T^{-2}]
common_ocr_confusions:
- corrupt: Js
  intended: J s
  entity: planck_constant_unit
- corrupt: Ns
  intended: N s
  entity: momentum_unit
- corrupt: Nm
  intended: N m
  entity: torque_unit
exam_traps:
- 'NEET: Planck''s constant (h) and angular momentum (L) have the SAME dimensions:
  [M L^2 T^-1].'
- 'NEET: Linear momentum [M L T^-1] and torque [M L^2 T^-2] do NOT have the same dimensions.'
- 'NEET: Work, torque, and energy share [M L^2 T^-2], but torque is a vector while
  work is scalar.'
- 'NEET: Impulse (J = F*Δt) and linear momentum have identical dimensions: [M L T^-1].'
- 'NEET: Surface tension and spring constant both have dimension [M T^-2].'
---

# Units and Measurement (Unit I: Physical World and Measurement)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. Physics is a quantitative science, based on measurement of physical quantities.  Certain
physical quantities have been chosen as fundamental or base quantities (such as
length, mass, time, electric current, thermodynamic temperature, amount of substance,
and luminous intensity).
2. Each base quantity is defined in terms of a certain basic, arbitrarily chosen but properly
standardised reference standard called unit (such as metre, kilogram, second, ampere,
kelvin, mole and candela).  The units for the fundamental or base quantities are called
fundamental or base units.
3. Other physical quantities, derived from the base quantities, can be expressed as a
combination of the base units and are called derived units.  A complete set of units,
both fundamental and derived, is called a system of units.
4. The International System of Units (SI) based on seven base units is at present
internationally accepted unit system and is widely used throughout the world.
5. The SI units are used in all physical measurements, for both the base quantities and
the derived quantities obtained from them.  Certain derived units are expressed by
means of SI units with special names (such as joule, newton, watt, etc).
6. The SI units have well defined and internationally accepted unit symbols (such as m
for metre, kg for kilogram, s for second, A for ampere, N for newton etc.).
7. Physical measurements are usually expressed for small and large quantities in scientific
notation, with powers of 10.  Scientific notation and the prefixes are used to simplify
measurement notation and numerical computation, giving indication to the precision
of the numbers.
8. Certain general rules and guidelines must be followed for using notations for physical
quantities and standard symbols for SI units, some other units and SI prefixes for
expressing properly the physical quantities and measurements.
9. In computing any physical quantity, the units for derived quantities involved in the
relationship(s) are tr

## 2. Canonical Formulas & Constraints
- **planck_energy**: `$E = h\nu \implies [h] = [M L^2 T^{-1}]$`
- **angular_momentum_def**: `$L = r \times p \implies [L] = [M L^2 T^{-1}]$`
- **torque_def**: `$\tau = r \times F \implies [\tau] = [M L^2 T^{-2}]$`
- **linear_momentum_def**: `$p = mv \implies [p] = [M L T^{-1}]$`
- **gravitational_constant**: `$G = \frac{F r^2}{m_1 m_2} \implies [G] = [M^{-1} L^3 T^{-2}]$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Planck's constant (h) and angular momentum (L) have the SAME dimensions: [M L^2 T^-1].
- ⚠️ NEET: Linear momentum [M L T^-1] and torque [M L^2 T^-2] do NOT have the same dimensions.
- ⚠️ NEET: Work, torque, and energy share [M L^2 T^-2], but torque is a vector while work is scalar.
- ⚠️ NEET: Impulse (J = F*Δt) and linear momentum have identical dimensions: [M L T^-1].
- ⚠️ NEET: Surface tension and spring constant both have dimension [M T^-2].

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph106](neet.phy.11.keph106)
- [neet.phy.11.keph107](neet.phy.11.keph107)
