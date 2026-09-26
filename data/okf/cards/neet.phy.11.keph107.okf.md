---
id: neet.phy.11.keph107
node_type: chapter
parent_id: neet.phy.11
children: []
related:
- neet.phy.11.keph101
- neet.phy.11.keph106
domain: phy
subject: physics
class: 11
chapter_num: 7
chapter_title: Gravitation
unit: 'Unit VI: Gravitation'
exam_tags:
- NEET
- JEE_Main
target_quantities:
- symbol: g
  name: acceleration due to gravity
  units:
  - m/s²
  dimension: LT^-2
- symbol: G
  name: universal gravitational constant
  units:
  - N m^2/kg^2
  dimension: M^-1L^3T^-2
- symbol: M
  name: mass of earth / body
  units:
  - kg
  dimension: M
- symbol: R
  name: radius of earth / orbit
  units:
  - m
  - km
  dimension: L
- symbol: v_e
  name: escape velocity
  units:
  - m/s
  - km/s
  dimension: LT^-1
- symbol: v_o
  name: orbital velocity
  units:
  - m/s
  - km/s
  dimension: LT^-1
canonical_formulas:
- id: gravity_surface
  formula: g = G*M/(R^2)
  latex: g = \frac{GM}{R^2}
- id: radius_halved_gravity
  formula: g' = G*M/((R/2)^2) = 4*g
  latex: g' = \frac{GM}{(R/2)^2} = 4g
- id: escape_velocity
  formula: v_e = sqrt(2*G*M/R) = sqrt(2*g*R)
  latex: v_e = \sqrt{\frac{2GM}{R}} = \sqrt{2gR}
- id: orbital_velocity
  formula: v_o = sqrt(G*M/R) = sqrt(g*R)
  latex: v_o = \sqrt{\frac{GM}{R}} = \sqrt{gR}
- id: kepler_third_law
  formula: T^2 = 4*pi^2*r^3/(G*M)
  latex: T^2 \propto r^3
common_ocr_confusions:
- corrupt: 11.2 km
  intended: 11.2 km/s
  entity: escape_velocity
- corrupt: 9.8 m/s
  intended: 9.8 m/s²
  entity: gravity
exam_traps:
- 'NEET: Diameter halved (mass constant) → g'' = 4g (quadruples).'
- 'NEET: Escape velocity is independent of mass of the projected body.'
- 'NEET: g = 0 at Earth''s centre; g decreases both above and below the surface.'
---

# Gravitation (Unit VI: Gravitation)
> **Standard:** Class 11 NCERT Physics | **Primary Target: NEET** | Also: JEE Main

## 1. Key Principles & Conceptual Summary
1. Newton’s law of universal gravitation states that the gravitational force of attraction between
any two particles of masses m1 and m2 separated by a distance r has the magnitude
F G
m m
r2= 1 2
where G is the universal gravitational constant, which has the value  6.672×10–11 N m2 kg–2.
2. If we have to find the resultant gravitational force acting on the particle m due to a number of
masses M1, M2, ….Mn etc. we use the principle of superposition. Let F1, F2, ….Fn be the individual
forces due to M1, M2, ….Mn, each given by the law of gravitation. From the principle of superposition
each force acts independently  and uninfluenced by the other bodies. The resultant force FR is
then found by vector addition
FR  =  F1 + F2 + ……+ Fn   =  
Fi
i
n
=
∑
1
where the symbol ‘Σ’ stands for summation.
3. Kepler’s laws of planetary motion state that
(a)All planets move in elliptical orbits with the Sun at one of the focal points
(b) The radius vector drawn from the Sun to a planet sweeps out equal areas in equal time
intervals. This follows from the fact that the force of gravitation on the planet is central
and hence angular momentum is conserved.
(c) The square of the orbital period of a planet is proportional to the cube of the   semi-major
axis of the elliptical orbit of the planet
The period T and radius R of the circular orbit of a planet about the Sun are related
by
3
2
2 4  RM  GT
s 




 π=
where Ms is the mass of the Sun. Most planets have nearly circular orbits about the Sun. For
elliptical orbits, the above equation is valid if R is replaced by the semi-major axis, a.
4. The acceleration due to gravity.
(a) at a height h above the earth’s surface
( )
2( )  
  
E
E
G Mg h
R h
=
+
≈ −


 1  2
2
G M
R
h
R
E
E E
   for h << RE
⊳Example 7.8  A 400 kg satellite is in a circular
orbit of radius 2RE about the Earth. How much
energy is required to transfer it to a circular
orbit of radius 4RE ? What are the changes in
the kinetic and potential energies ?
Answer

## 2. Canonical Formulas & Constraints
- **gravity_surface**: `$g = \frac{GM}{R^2}$`
- **radius_halved_gravity**: `$g' = \frac{GM}{(R/2)^2} = 4g$`
- **escape_velocity**: `$v_e = \sqrt{\frac{2GM}{R}} = \sqrt{2gR}$`
- **orbital_velocity**: `$v_o = \sqrt{\frac{GM}{R}} = \sqrt{gR}$`
- **kepler_third_law**: `$T^2 \propto r^3$`

## 3. High-Yield NEET Exam Traps
- ⚠️ NEET: Diameter halved (mass constant) → g' = 4g (quadruples).
- ⚠️ NEET: Escape velocity is independent of mass of the projected body.
- ⚠️ NEET: g = 0 at Earth's centre; g decreases both above and below the surface.

## 4. Related Nodes (OKF Graph Links)
- [neet.phy.11.keph101](neet.phy.11.keph101)
- [neet.phy.11.keph106](neet.phy.11.keph106)
