---
id: neet.phy.11.keph104
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph102
- neet.phy.11.keph105
domain: phy
subject: physics
class: 11
chapter_num: 4
chapter_title: Laws of Motion
unit: 'Unit III: Laws of Motion'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: F
  name: force
  units:
  - N
  - kN
  dimension: MLT^-2
- symbol: m
  name: mass
  units:
  - kg
  - g
  dimension: M
- symbol: a
  name: acceleration
  units:
  - m/s²
  dimension: LT^-2
- symbol: p
  name: linear momentum
  units:
  - kg m/s
  dimension: MLT^-1
- symbol: mu
  name: coefficient of friction
  units:
  - ''
  dimension: '1'
canonical_formulas:
- id: newton_second_law
  formula: F = m * a
  latex: F = ma
- id: momentum
  formula: p = m * v
  latex: p = mv
- id: impulse
  formula: J = F * delta_t = delta_p
  latex: J = F\Delta t = \Delta p
- id: friction
  formula: f_max = mu * N
  latex: f_{max} = \mu N
common_ocr_confusions:
- corrupt: l500kg
  intended: 1500 kg
  entity: mass
- corrupt: isn
  intended: 15N
  entity: force
exam_traps:
- 'NEET: Apparent weight in elevator: W_app = m(g+a) going up, m(g−a) going down.'
- 'NEET: Static friction adjusts itself up to f_s = μ_s·N (self-adjusting).'
- 'NEET: Pseudo-force acts in non-inertial frames only.'
---

# Laws of Motion (Unit III: Laws of Motion)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. Aristotle’s view that a force is necessary to keep a body in uniform motion is wrong.  A
force is necessary in practice to counter the opposing force of friction.
2. Galileo extrapolated simple observations on motion of bodies on inclined planes, and
arrived at the law of inertia.  Newton’s first law of motion is the same law rephrased
thus: “Everybody continues to be in its state of rest or of uniform motion in a straight line,
unless compelled by some external force to act otherwise”.  In simple terms, the First Law
is “If external force on a body is zero, its acceleration is zero”.
3. Momentum (p ) of a body is the product of its mass (m) and velocity (v) :
p  =  m v
4. Newton’s second law of motion :
The rate of change of momentum of a body is proportional to the applied force and takes
place in the direction in which the force acts.  
Thus
d
d
k k m 
t
= = pF a
where F is the net external force on the body and a its acceleration. We set the constant
of proportionality k = 1 in SI units.  Then
d
d
m
t
= = pF a
The SI unit of force is newton : 1 N = 1 kg m s-2.
(a) The second law is consistent with the First Law ( F = 0 implies a = 0)
(b) It is a vector equation
(c) It is applicable to a particle, and also to a body or a system of particles, provided  F
is the total external force on the system and a  is the acceleration of the system as
a whole.
(d) F at a point at a certain instant determines a at the same point at that instant.
That is the Second Law is a local law; a at an instant does not depend on the
history of motion.
4. Impulse is the product of force and time which equals change in momentum.
The notion of impulse is useful when a large force acts for a short time to produce a
measurable change in momentum. Since the time of action of the force is very short,
one can assume that there is no appreciable change in the position of the body during
the action of the impulsive force.
6. Newton’s third law of motion:
To every action, there is always an equal

## 2. Canonical Formulas & Constraints
- **newton_second_law**: `$F = ma$`
- **momentum**: `$p = mv$`
- **impulse**: `$J = F\Delta t = \Delta p$`
- **friction**: `$f_{max} = \mu N$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Apparent weight in elevator: W_app = m(g+a) going up, m(g−a) going down.
- ⚠️ NEET: Static friction adjusts itself up to f_s = μ_s·N (self-adjusting).
- ⚠️ NEET: Pseudo-force acts in non-inertial frames only.

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph102](neet.phy.11.keph102)
- [neet.phy.11.keph105](neet.phy.11.keph105)
