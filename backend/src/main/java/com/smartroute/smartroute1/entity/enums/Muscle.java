package com.smartroute.smartroute1.entity.enums;

public enum Muscle {

    shins(BodyPart.LOWER_LEG_REGION),
    hands(BodyPart.UPPER_REGION),
    sternocleidomastoid(BodyPart.NECK_REGION),
    soleus(BodyPart.LOWER_LEG_REGION),

    inner_thighs(BodyPart.HIP),
    lower_abs(BodyPart.CORE_REGION),
    grip_muscles(BodyPart.UPPER_REGION),
    abdominals(BodyPart.CORE_REGION),
    wrist_extensors(BodyPart.UPPER_REGION),
    wrist_flexors(BodyPart.UPPER_REGION),
    latissimus_dorsi(BodyPart.UPPER_REGION),
    upper_chest(BodyPart.UPPER_REGION),
    rotator_cuff(BodyPart.UPPER_REGION),
    wrists(BodyPart.UPPER_REGION),

    groin(BodyPart.HIP),
    brachialis(BodyPart.UPPER_REGION),
    deltoids(BodyPart.UPPER_REGION),
    feet(BodyPart.FEET_REGION),
    ankles(BodyPart.FEET_REGION),
    trapezius(BodyPart.UPPER_REGION),
    rear_deltoids(BodyPart.UPPER_REGION),
    chest(BodyPart.UPPER_REGION),

    quadriceps(BodyPart.UPPER_LEG_REGION),
    back(BodyPart.UPPER_REGION),
    core(BodyPart.CORE_REGION),
    shoulders(BodyPart.UPPER_REGION),
    ankle_stabilizers(BodyPart.FEET_REGION),
    rhomboids(BodyPart.UPPER_REGION),
    obliques(BodyPart.CORE_REGION),
    lower_back(BodyPart.CORE_REGION),

    hip_flexors(BodyPart.HIP),
    levator_scapulae(BodyPart.NECK_REGION),
    abductors(BodyPart.HIP),
    serratus_anterior(BodyPart.UPPER_REGION),
    traps(BodyPart.UPPER_REGION),
    forearms(BodyPart.UPPER_REGION),
    delts(BodyPart.UPPER_REGION),
    biceps(BodyPart.UPPER_REGION),
    upper_back(BodyPart.UPPER_REGION),
    spine(BodyPart.SPINAL_INJURY),
    cardiovascular_system(BodyPart.RESPIRATION_REGION),
    triceps(BodyPart.UPPER_REGION),

    adductors(BodyPart.HIP),
    hamstrings(BodyPart.UPPER_LEG_REGION),
    glutes(BodyPart.HIP),

    pectorals(BodyPart.UPPER_REGION),
    calves(BodyPart.LOWER_LEG_REGION),
    lats(BodyPart.UPPER_REGION),
    quads(BodyPart.UPPER_LEG_REGION),
    abs(BodyPart.CORE_REGION),
    upper_legs(BodyPart.UPPER_LEG_REGION),
    lower_legs(BodyPart.LOWER_LEG_REGION),

    do_not_exercise(null);

    private final BodyPart bodyPart;

    Muscle(BodyPart bodyPart) {
        this.bodyPart = bodyPart;
    }

    public BodyPart getBodyPart() {
        return bodyPart;
    }
}