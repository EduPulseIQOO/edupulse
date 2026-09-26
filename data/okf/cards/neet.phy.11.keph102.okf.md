---
id: neet.phy.11.keph102
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph103
- neet.phy.11.keph104
domain: phy
subject: physics
class: 11
chapter_num: 2
chapter_title: Motion in a Straight Line
unit: 'Unit II: Kinematics'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: u
  name: initial velocity
  units:
  - m/s
  - km/h
  dimension: LT^-1
- symbol: v
  name: final velocity
  units:
  - m/s
  - km/h
  dimension: LT^-1
- symbol: a
  name: acceleration / retardation
  units:
  - m/s²
  dimension: LT^-2
- symbol: s
  name: displacement / distance
  units:
  - m
  - km
  dimension: L
- symbol: t
  name: time
  units:
  - s
  - sec
  dimension: T
canonical_formulas:
- id: first_equation
  formula: v = u + a * t
  latex: v = u + at
- id: second_equation
  formula: s = u * t + 0.5 * a * t^2
  latex: s = ut + \frac{1}{2}at^2
- id: third_equation
  formula: v^2 = u^2 + 2 * a * s
  latex: v^2 = u^2 + 2as
- id: stopping_dist
  formula: s = u^2 / (2 * a)
  latex: s = \frac{u^2}{2a}
  constraints:
  - v == 0
- id: stopping_time
  formula: t = u / a
  latex: t = \frac{u}{a}
  constraints:
  - v == 0
common_ocr_confusions:
- corrupt: io m/s
  intended: 10 m/s
  entity: velocity
- corrupt: u seconds
  intended: 4 seconds
  entity: time
- corrupt: skg
  intended: 5 kg
  entity: mass
exam_traps:
- 'NEET: Retarding force means acceleration is negative (deceleration). Use a = -ve
  value.'
- 'NEET/JEE: If speed is doubled, stopping distance quadruples (s ∝ u²).'
- 'NEET: Average velocity = (u + v)/2 only for uniform acceleration.'
---

# Motion in a Straight Line (Unit II: Kinematics)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. The origin and the positive direction of an axis are a matter of choice. You should first specify
this choice before you assign signs to quantities like displacement, velocity and acceleration.
2. If a particle is speeding up, acceleration is in the direction of velocity; if its speed is
decreasing, acceleration is in the direction opposite to that of the velocity.  This
statement is independent of the choice of the origin and the axis.
3. The sign of acceleration does not tell us whether the particle’s speed is increasing or
decreasing.  The sign of acceleration (as mentioned in point 3) depends on the choice
of the positive direction of the axis.  For example, if the vertically upward direction is
chosen to be the positive direction of the axis, the acceleration due to gravity is
negative.  If a particle is falling under gravity, this acceleration, though negative,
results in increase in speed.  For a particle thrown upward, the same negative
acceleration (of gravity) results in decrease in speed.
4. The zero velocity of a particle at any instant does not necessarily imply zero acceleration
at that instant.  A particle may be momentarily at rest and yet have non-zero acceleration.
For example, a particle thrown up has zero velocity at its uppermost point but the
acceleration at that instant continues to be the acceleration due to gravity.
5. In the kinematic equations of motion [Eq. (2.9)], the various quantities are algebraic,
i.e. they may be positive or negative.  The equations are applicable in all situations
(for one dimensional motion with constant acceleration) provided the values of different
quantities are substituted in the equations with proper signs.
6. The definitions of instantaneous velocity and acceleration (Eqs. (2.1) and (2.3)) are
exact and are always correct while the kinematic equations (Eq. (2.9)) are true only for
motion in which the magnitude and the direction of acceleration are constant during
the course of motion.
Reprint 2025-26

PHY

## 2. Canonical Formulas & Constraints
- **first_equation**: `$v = u + at$`
- **second_equation**: `$s = ut + \frac{1}{2}at^2$`
- **third_equation**: `$v^2 = u^2 + 2as$`
- **stopping_dist**: `$s = \frac{u^2}{2a}$`
- **stopping_time**: `$t = \frac{u}{a}$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Retarding force means acceleration is negative (deceleration). Use a = -ve value.
- ⚠️ NEET/JEE: If speed is doubled, stopping distance quadruples (s ∝ u²).
- ⚠️ NEET: Average velocity = (u + v)/2 only for uniform acceleration.

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph103](neet.phy.11.keph103)
- [neet.phy.11.keph104](neet.phy.11.keph104)
