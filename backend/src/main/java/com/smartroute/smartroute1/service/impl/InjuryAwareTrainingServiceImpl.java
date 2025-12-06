package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.CreateInjuryStateDto;
import com.smartroute.smartroute1.endpoint.dto.UpdateInjuryDto;
import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.Injuries;
import com.smartroute.smartroute1.entity.enums.BodyPart;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.repository.InjuryRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.InjuryAwareTrainingService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class InjuryAwareTrainingServiceImpl implements InjuryAwareTrainingService {

    private static final double ALPHA_INTENSITY = 2.5;
    private static final double BETA_VOLUME = 0.6;
    private static final double K_IMPACT = 10.0;
    private static final double I0_IMPACT = 0.4;

    private static final double ALPHA_REGION_INJURED = 5.0;
    private static final double DELTA_REGION_NONINJURED = 0.1;

    private final InjuryRepository injuryRepository;
    private final UserRepository userRepository;

    public InjuryAwareTrainingServiceImpl(InjuryRepository injuryRepository, UserRepository userRepository) {
        this.injuryRepository = injuryRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Map<BodyPart, Double> calculateInjuriesMap(List<Injuries> injuries) {
        Map<BodyPart, Double> result = new EnumMap<>(BodyPart.class);

        // default: no restriction
        for (BodyPart part : BodyPart.values()) {
            result.put(part, 1.0);
        }

        if (injuries == null || injuries.isEmpty()) {
            return result;
        }

        LocalDate today = LocalDate.now();
        int windowDays = 14;

        if (hasFullStopInjury(injuries)) {
            for (BodyPart part : BodyPart.values()) {
                result.put(part, 0.0);
            }
            return result;
        }

        for (BodyPart region : BodyPart.values()) {
            double regionConstraint = 1.0;
            for (Injuries injury : injuries) {
                LocalDate lastInjuryDate = injury.getLastInjuryDate();
                long daysAgo;
                if (lastInjuryDate == null) {
                    daysAgo = 0;
                } else {
                    daysAgo = ChronoUnit.DAYS.between(lastInjuryDate, today);

                }
                if (daysAgo > windowDays) {
                    continue;
                }
                double baseConstraint = getConstraintFromThisInjury(region, injury);
                double freshnessFactor = (windowDays - daysAgo) / (double) windowDays;
                double constraintFromThisInjury = 1.0 - freshnessFactor * (1.0 - baseConstraint);
                regionConstraint = Math.min(regionConstraint, clamp01(constraintFromThisInjury));
            }
            result.put(region, regionConstraint);
        }
        return result;
    }


    private boolean hasFullStopInjury(List<Injuries> injuries) {
        LocalDate today = LocalDate.now();
        int windowDays = 14;

        for (Injuries injury : injuries) {
            BodyPart area = injury.getAffectedArea();
            LocalDate lastInjuryDate = injury.getLastInjuryDate();
            if (lastInjuryDate == null) {
                if (area == BodyPart.BONE_FRACTURE
                        || area == BodyPart.SPINAL_INJURY
                        || area == BodyPart.RESPIRATION_REGION) {
                    return true;
                } else {
                    continue;
                }
            }

            long daysAgo = ChronoUnit.DAYS.between(lastInjuryDate, today);
            if (daysAgo > windowDays) {
                continue;
            }
            if (area == BodyPart.BONE_FRACTURE
                    || area == BodyPart.SPINAL_INJURY
                    || area == BodyPart.RESPIRATION_REGION) {
                return true;
            }


        }
        return false;
    }

    private double getConstraintFromThisInjury(BodyPart region, Injuries injury) {
        double i = clamp01(injury.getInjuryIndex());

        double constraintFromThisInjury;
        if (region == injury.getAffectedArea()) {
            constraintFromThisInjury = Math.exp(-ALPHA_REGION_INJURED * i);
        } else {
            constraintFromThisInjury = 1.0 - DELTA_REGION_NONINJURED * i;
        }
        return constraintFromThisInjury;
    }

    @Override
    public double calculateIntensityScaling(double injuryIndex) {
        double i = clamp01(injuryIndex);
        return Math.exp(-ALPHA_INTENSITY * i);
    }

    @Override
    public double calculateVolumeScaling(double injuryIndex) {
        double i = clamp01(injuryIndex);
        double value = 1.0 - BETA_VOLUME * i;
        return clamp01(value);
    }

    @Override
    public double calculateHighImpactPenalty(double injuryIndex) {
        double i = clamp01(injuryIndex);
        double exponent = K_IMPACT * (i - I0_IMPACT);
        double denominator = 1.0 + Math.exp(exponent);
        return 1.0 / denominator;
    }

    @Override
    public Injuries createInjuries(CreateInjuryStateDto injury, String email) {
        Injuries injuries = new Injuries();
        injuries.setInjuryIndex(injury.getInjuryIndex());
        injuries.setAffectedArea(injury.getAffectedArea());
        injuries.setLastHealthyDate(injury.getLastHealthyDate());
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            return null;
        }
        injuries.setApplicationUser(user);
        injuryRepository.save(injuries);
        return injuries;
    }

    @Override
    public Injuries updateInjuries(UpdateInjuryDto injury, String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        Injuries in = injuryRepository.findByIdAndApplicationUser(injury.getInjuryId(), user);
        if (in == null) {
            return null;
        }
        in.setLastInjuryDate(injury.getLastInjuryDate());
        in.setInjuryIndex(injury.getInjuryIndex());
        in.setAffectedArea(injury.getAffectedArea());
        in.setLastHealthyDate(injury.getLastHealthyDate());
        return injuryRepository.save(in);
    }

    @Override
    public List<Injuries> findInjuriesByEmail(String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        return injuryRepository.getAllByApplicationUser(user);
    }

    @Override
    public void deleteInjuriesByEmailAndId(String email, long id) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (injuryRepository.findByIdAndApplicationUser(id, user) == null) {
            throw new NotFoundException("Injuries with id " + id + " not found");
        }
        injuryRepository.deleteById(id);
    }

    @Override
    public double getInjuryIndex(String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            return 0.0;
        }

        List<Injuries> injuries = injuryRepository.getAllByApplicationUser(user);
        if (injuries == null || injuries.isEmpty()) {
            return 0.0;
        }

        if (this.hasFullStopInjury(injuries)) {
            return 1.0;
        }

        LocalDate today = LocalDate.now();
        int windowDays = 14;

        double totalWeightedIndex = 0.0;

        for (Injuries injury : injuries) {
            LocalDate lastInjuryDate = injury.getLastInjuryDate();
            long daysAgo;

            if (lastInjuryDate == null) {
                daysAgo = 0;
            } else {
                daysAgo = ChronoUnit.DAYS.between(lastInjuryDate, today);
            }

            if (daysAgo > windowDays) {
                continue;
            }

            double baseIndex = clamp01(injury.getInjuryIndex());
            double freshnessFactor = (windowDays - daysAgo) / (double) windowDays;

            totalWeightedIndex = Math.max(totalWeightedIndex, baseIndex * freshnessFactor);
        }


        // Weighted average of all active injuries
        return totalWeightedIndex;
    }

    @Override
    public double getInjuryConstraint(String email) {
        ApplicationUser user = userRepository.findUserByEmail(email);
        if (user == null) {
            return 1.0;
        }

        List<Injuries> injuries = injuryRepository.getAllByApplicationUser(user);
        if (injuries == null || injuries.isEmpty()) {
            return 1.0;
        }
        double injuryIndex = getInjuryIndex(email);

        double intensityScaling = calculateIntensityScaling(injuryIndex);
        double volumeScaling = calculateVolumeScaling(injuryIndex);
        double impactPenalty = calculateHighImpactPenalty(injuryIndex);

        return (intensityScaling + volumeScaling + impactPenalty) / 3.0;
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}
