package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.CreateInjuryStateDto;
import com.smartroute.smartroute1.endpoint.dto.UpdateInjuryDto;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for applying the Injury-Aware Training logic as defined in the training model specification.
 *
 * <p>The module converts one or more {@link CreateInjuryStateDto} entries into:
 * <ul>
 *     <li>Global scaling factors for training intensity, volume, and high-impact movements (Section 5.3.1)</li>
 *     <li>A regional constraint map that suppresses exercises involving affected body regions (Section 5.3.2)</li>
 * </ul>
 *
 * <h3>Global Modulation Functions (Section 5.3.1)</h3>
 * For a given injury index {@code I ∈ [0,1]}:
 * <ul>
 *     <li><b>Intensity scaling:</b> F<sub>intensity</sub>(I) = exp(−α · I)</li>
 *     <li><b>Volume scaling:</b>    F<sub>volume</sub>(I) = 1 − β · I</li>
 *     <li><b>High-impact penalty:</b> F<sub>impact</sub>(I) = 1 / (1 + exp(k · (I − I₀)))</li>
 * </ul>
 *
 * <p>These factors adjust global training recommendations based on the overall severity of the injury.
 *
 * <h3>Regional Constraint Map (Section 5.3.2)</h3>
 * Each injury also restricts specific body regions. The returned map contains, for every
 * coarse {@link BodyPart} region, a value {@code C_b ∈ [0,1]} which represents how much of the normal
 * load is still allowed for that region. A value of {@code 1.0} means unrestricted training; {@code 0.0}
 * means complete avoidance.
 *
 * <p>When multiple injuries are present, restrictions are combined conservatively using the minimum
 * constraint for each region.
 *
 * <p>These constraints are used by the training-plan generator to scale exercise intensity and
 * suppress unsafe movements.</p>
 */
@Service
public interface InjuryAwareTrainingService {
    /**
     * Computes a composite constraint map for all coarse {@link BodyPart} regions
     * based on the provided injury states.
     *
     * <p>For each region {@code b}, the returned value {@code C_b ∈ [0,1]} represents
     * how much of the normal load is allowed after accounting for all injuries.
     * Multiple injuries are combined conservatively (i.e. the minimum constraint
     * across all injuries).
     *
     * @param injuries the list of active injuries (may be empty)
     * @return a map from each {@link BodyPart} to a constraint factor {@code ∈ [0,1]}
     */
    Map<BodyPart, Double> calculateInjuriesMap(List<Injuries> injuries);

    /**
     * Computes the global intensity scaling factor F<sub>intensity</sub>(I)
     * defined as {@code exp(−α · I)}.
     *
     * <p>This factor reduces training intensity proportionally to the
     * athlete’s global injury severity.
     *
     * @param injuryIndex I ∈ [0,1], where 0 = healthy and 1 = severe injury
     * @return the intensity scaling factor ∈ [0,1]
     */
    double calculateIntensityScaling(double injuryIndex);

    /**
     * Computes the global volume scaling factor F<sub>volume</sub>(I)
     * defined as {@code 1 − β · I}.
     *
     * <p>This factor linearly reduces training volume (duration, distance, or total work)
     * based on injury severity.
     *
     * @param injuryIndex I ∈ [0,1]
     * @return the volume scaling factor ∈ [0,1]
     */
    double calculateVolumeScaling(double injuryIndex);

    /**
     * Computes the high-impact activity penalty F<sub>impact</sub>(I), defined as:
     * <pre>
     *     1 / (1 + exp(k · (I − I₀)))
     * </pre>
     * This factor specifically suppresses high-impact movements (running, jumping)
     * when the injury index exceeds a threshold (typically I₀ = 0.4).
     *
     * @param injuryIndex I ∈ [0,1]
     * @return the high-impact penalty factor ∈ [0,1]
     */
    double calculateHighImpactPenalty(double injuryIndex);


    /**
     * Creates a new injury record for the specified user.
     *
     * @param injury the DTO containing the details of the injury to create
     * @param email the email of the user for whom the injury record is created
     * @return the persisted {@link Injuries} entity
     */
    Injuries createInjuries(CreateInjuryStateDto injury, String email);

    /**
     * Updates an existing injury record for the specified user.
     *
     * @param injury the DTO containing updated values and the identifier
     *               of the injury to update
     * @param email the email of the user whose injury record is being updated
     * @return the updated {@link Injuries} entity
     */
    Injuries updateInjuries(UpdateInjuryDto injury, String email);

    /**
     * Retrieves all injuries record for the specified user.
     *
     * @param email the email of the user whose injury record should be retrieved
     * @return a list with the corresponding {@link Injuries} entity
     * @throws jakarta.persistence.EntityNotFoundException
     *         if no matching injury for the given user and ID exists
     */
    List<Injuries> findInjuriesByEmail(String email);

    /**
     * Deletes one injury by its id and the corresponding User email.
     * When there is no Injury with that ID for that user it will throw a NOT_FOUND Error.
     *
     * @param email the user's Email
     * @param id the id of the Injury to delete
     */
    void deleteInjuriesByEmailAndId(String email, long id);

}
