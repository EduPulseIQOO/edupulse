---
id: neet.phy.11.keph105
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph104
- neet.phy.11.keph106
domain: phy
subject: physics
class: 11
chapter_num: 5
chapter_title: Work, Energy and Power
unit: 'Unit IV: Work, Energy and Power'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: W
  name: work done
  units:
  - J
  - kJ
  dimension: ML^2T^-2
- symbol: K
  name: kinetic energy
  units:
  - J
  dimension: ML^2T^-2
- symbol: p
  name: linear momentum
  units:
  - kg m/s
  dimension: MLT^-1
- symbol: U
  name: potential energy
  units:
  - J
  dimension: ML^2T^-2
- symbol: P
  name: power
  units:
  - W
  - kW
  dimension: ML^2T^-3
canonical_formulas:
- id: work
  formula: W = F * d * cos(theta)
  latex: W = \vec{F}\cdot\vec{d}
- id: kinetic_energy
  formula: K = 0.5*m*v^2 = p^2/(2*m)
  latex: K = \frac{1}{2}mv^2 = \frac{p^2}{2m}
- id: momentum_from_kinetic
  formula: p = sqrt(2*m*K)
  latex: p = \sqrt{2mK}
- id: work_energy_theorem
  formula: W_net = K_f - K_i
  latex: W_{net} = K_f - K_i
- id: power
  formula: P = W/t = F*v
  latex: P = \frac{W}{t} = \vec{F}\cdot\vec{v}
common_ocr_confusions:
- corrupt: l00J
  intended: 100 J
  entity: energy
- corrupt: skW
  intended: 5 kW
  entity: power
exam_traps:
- 'NEET: Work done by centripetal force is always ZERO (force ⊥ velocity).'
- 'NEET: Equal KE → momentum ratio p1/p2 = √(m1/m2).'
- 'NEET: Elastic collision conserves both KE and momentum; inelastic conserves only
  momentum.'
---

# Work, Energy and Power (Unit IV: Work, Energy and Power)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. The phrase ‘calculate the work done’ is incomplete. W e should refer (or imply
clearly by context) to the work done by a specific force or a group of forces on a
given  body over a certain displacement.
2. Work done is a scalar quantity. It can be positive or negative unlike mass and
kinetic energy which are positive scalar quantities. The work done by the friction
or viscous force on a moving body is negative.
3. For two bodies, the sum of the mutual forces exerted between them is zero from
Newton’s Third Law,
F12  +  F21  =  0
But the sum of the work done by the two forces need not always cancel, i.e.
W12  + W21  
≠   0
However, it may sometimes be true.
4. The work done by a force can be calculated sometimes even if the exact nature of
the force is not known. This is clear from Example 5.2 where the WE theorem is
used in such a situation.
5. The WE theorem is not independent of Newton’s Second Law. The WE theorem
may be viewed as a scalar form of the Second Law. The principle of conservation
of mechanical energy may be viewed as a consequence of the  WE theorem for
conservative forces.
5. The WE theorem holds in all inertial frames. It can also be extended to non-
inertial frames provided we include the pseudoforces in the calculation of the
net force acting on the body under consideration.
7. The potential energy of a body subjected to a conservative force is always
undetermined upto a constant. For example, the point where the potential
energy is zero is a matter of choice. For the gravitational  potential energy mgh,
the zero of the potential energy is chosen to be the ground. For the spring
potential energy kx2/2 , the zero of the potential energy is the equilibrium position
of the oscillating mass.
8. Every force encountered in mechanics does not have an associated potential
energy. For example, work done by friction over a closed path is not zero and no
potential energy can be associated with friction.
9. During a collision : (a) the total linear momentu

## 2. Canonical Formulas & Constraints
- **work**: `$W = \vec{F}\cdot\vec{d}$`
- **kinetic_energy**: `$K = \frac{1}{2}mv^2 = \frac{p^2}{2m}$`
- **momentum_from_kinetic**: `$p = \sqrt{2mK}$`
- **work_energy_theorem**: `$W_{net} = K_f - K_i$`
- **power**: `$P = \frac{W}{t} = \vec{F}\cdot\vec{v}$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Work done by centripetal force is always ZERO (force ⊥ velocity).
- ⚠️ NEET: Equal KE → momentum ratio p1/p2 = √(m1/m2).
- ⚠️ NEET: Elastic collision conserves both KE and momentum; inelastic conserves only momentum.

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph104](neet.phy.11.keph104)
- [neet.phy.11.keph106](neet.phy.11.keph106)
