---
id: neet.phy.11.keph106
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph105
- neet.phy.11.keph107
domain: phy
subject: physics
class: 11
chapter_num: 6
chapter_title: Systems of Particles and Rotational Motion
unit: 'Unit V: Motion of System of Particles'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: tau
  name: torque
  units:
  - N m
  dimension: ML^2T^-2
- symbol: L
  name: angular momentum
  units:
  - kg m^2/s
  dimension: ML^2T^-1
- symbol: I
  name: moment of inertia
  units:
  - kg m^2
  dimension: ML^2
- symbol: omega
  name: angular velocity
  units:
  - rad/s
  dimension: T^-1
- symbol: alpha
  name: angular acceleration
  units:
  - rad/s²
  dimension: T^-2
canonical_formulas:
- id: torque_relation
  formula: tau = I * alpha
  latex: \tau = I\alpha
- id: angular_momentum
  formula: L = I * omega = r * p
  latex: L = I\omega = \vec{r}\times\vec{p}
- id: rotational_ke
  formula: K_rot = 0.5*I*omega^2 = L^2/(2*I)
  latex: K_{rot}=\frac{1}{2}I\omega^2=\frac{L^2}{2I}
- id: parallel_axis
  formula: I = I_cm + m * d^2
  latex: I = I_{cm} + md^2
common_ocr_confusions:
- corrupt: rad/s2
  intended: rad/s²
  entity: angular_acceleration
exam_traps:
- 'NEET: No external torque → angular momentum conserved (I₁ω₁ = I₂ω₂).'
- 'NEET: Torque dimensions [ML²T⁻²] = energy dimensions, but torque is a vector.'
- 'NEET: Moment of inertia of ring = MR², disk = MR²/2, solid sphere = 2MR²/5.'
---

# Systems of Particles and Rotational Motion (Unit V: Motion of System of Particles)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. Ideally, a rigid body is one for which the distances between different particles of the
body do not change, even though there are forces on them.
2. A rigid body fixed at one point or along a line can have only rotational motion. A rigid
body not fixed in some way can have either pure translational motion or a combination
of translational and rotational motions.
3. In rotation about a fixed axis, every particle of the rigid body moves in a circle which
lies in a plane perpendicular to the axis and has its centre on the axis. Every Point in
the rotating rigid body has the same angular velocity at any instant of time.
4. In pure translation, every particle of the body moves with the same velocity at any
instant of time.
5. Angular velocity is a vector. Its magnitude is 
ω = dθ/dt and it is directed along the axis
of rotation. For rotation about a fixed axis, this vector ω ωω ωω has a fixed direction.
6. The vector or cross product of two vector a and b is a vector written as a×b. The
magnitude of this vector is absinθ  and its direction is given by the right handed screw
or the right hand rule.
7. The linear velocity of a particle of a rigid body rotating about a fixed axis is given by
v = ω ωω ωω × r, where r is the position vector of the particle with respect to an origin along the
fixed axis. The relation applies even to more general rotation of a rigid body with one
point fixed. In that case r is the position vector of the particle with respect to the fixed
point taken as the origin.
8. The centre of mass of a system of n particles is defined as the point whose position
vector is
R
r
= ∑ m
M
i i
9. Velocity of the centre of mass of a system of particles is given by V = P/M, where P is the
linear momentum of the system. The centre of mass moves as if all the mass of the
system is concentrated at this point and all the external forces act at it. If the total
external force on the system is zero, then the total linear momentum of the system is
constant.
10. The an

## 2. Canonical Formulas & Constraints
- **torque_relation**: `$\tau = I\alpha$`
- **angular_momentum**: `$L = I\omega = \vec{r}\times\vec{p}$`
- **rotational_ke**: `$K_{rot}=\frac{1}{2}I\omega^2=\frac{L^2}{2I}$`
- **parallel_axis**: `$I = I_{cm} + md^2$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: No external torque → angular momentum conserved (I₁ω₁ = I₂ω₂).
- ⚠️ NEET: Torque dimensions [ML²T⁻²] = energy dimensions, but torque is a vector.
- ⚠️ NEET: Moment of inertia of ring = MR², disk = MR²/2, solid sphere = 2MR²/5.

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph105](neet.phy.11.keph105)
- [neet.phy.11.keph107](neet.phy.11.keph107)
