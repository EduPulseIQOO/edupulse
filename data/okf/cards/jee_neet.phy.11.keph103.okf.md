---
id: jee_neet.phy.11.keph103
domain: physics
subject: physics
class: 11
chapter_num: 3
chapter_title: Motion in a Plane
unit: 'Unit II: Kinematics'
exam_tags:
- JEE_Main
- NEET
target_quantities:
- symbol: u
  name: initial speed
  units:
  - m/s
  dimension: LT^-1
- symbol: theta
  name: angle of projection
  units:
  - deg
  - rad
  dimension: '1'
- symbol: R
  name: horizontal range
  units:
  - m
  dimension: L
- symbol: H
  name: maximum height
  units:
  - m
  dimension: L
- symbol: T
  name: time of flight
  units:
  - s
  dimension: T
- symbol: g
  name: acceleration due to gravity
  units:
  - m/s²
  - m/s^2
  dimension: LT^-2
canonical_formulas:
- id: time_of_flight
  formula: T = (2 * u * sin(theta)) / g
  latex: T = \frac{2u \sin\theta}{g}
- id: max_height
  formula: H = (u^2 * (sin(theta))^2) / (2 * g)
  latex: H = \frac{u^2 \sin^2\theta}{2g}
- id: horizontal_range
  formula: R = (u^2 * sin(2 * theta)) / g
  latex: R = \frac{u^2 \sin(2\theta)}{g}
common_ocr_confusions:
- corrupt: 9.8 m/s
  intended: 9.8 m/s²
  entity: gravity
- corrupt: 3O deg
  intended: 30 deg
  entity: angle
exam_traps:
- 'Range is maximum at 45 degrees: R_max = u^2 / g.'
- Two complementary angles (theta and 90 - theta) yield the exact same horizontal
  range R.
---

# Motion in a Plane (Unit II: Kinematics)
> **Standard:** Class 11 NCERT Physics | Target: JEE Main & NEET

## 1. Key Principles & Conceptual Summary
1. The path length traversed by an object between two points is, in general, not the same as
the magnitude of displacement. The displacement depends only on the end points; the
path length (as the name implies) depends on the actual path. The two quantities are
equal only if the object does not change its direction during the course of motion. In all
other cases, the path length is greater than the magnitude of displacement.
2. In view of point 1 above, the average speed of an object is greater than or equal to the
magnitude of the average velocity over a given time interval. The two are equal only if the
path length is equal to the magnitude of displacement.
3. The vector equations (3.33a) and (3.34a) do not involve any choice of axes. Of course,
you can always resolve them along any two independent axes.
4. The kinematic equations for uniform acceleration do not apply to the case of uniform
circular motion since in this case the magnitude of acceleration is constant but its
direction is changing.
5. An object subjected to two velocities v1 and v2 has a resultant velocity v = v1 + v2. Take
care to distinguish it from velocity of object 1 relative to velocity of object 2 : v12= v1 − v2.
Here v1 and v2 are velocities with reference to some common reference frame.
6. The resultant acceleration of an object in circular motion is towards the centre only if
the speed is constant.
7. The shape of the trajectory of the motion of an object is not determined by the acceleration
alone but also depends on the initial conditions of motion ( initial position and initial
velocity). For example, the trajectory of an object moving under the same acceleration
due to gravity can be a straight line or a parabola depending on the initial conditions.

## 2. Canonical Formulas & Constraints
- **time_of_flight**: `$T = \frac{2u \sin\theta}{g}$`
- **max_height**: `$H = \frac{u^2 \sin^2\theta}{2g}$`
- **horizontal_range**: `$R = \frac{u^2 \sin(2\theta)}{g}$`

## 3. High-Yield Exam Traps (JEE & NEET)
- ⚠️ Range is maximum at 45 degrees: R_max = u^2 / g.
- ⚠️ Two complementary angles (theta and 90 - theta) yield the exact same horizontal range R.
