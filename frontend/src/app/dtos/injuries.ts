/**
 * Enum for body parts - matches backend enum
 */
export enum BodyPart {
  NECK_REGION = 'NECK_REGION',
  UPPER_REGION = 'UPPER_REGION',
  CORE_REGION = 'CORE_REGION',
  KNEE_REGION = 'KNEE_REGION',
  FEET_REGION = 'FEET_REGION',
  UPPER_LEG_REGION = 'UPPER_LEG_REGION',
  LOWER_LEG_REGION = 'LOWER_LEG_REGION',
  RESPIRATION_REGION = 'RESPIRATION_REGION',
  SPINAL_INJURY = 'SPINAL_INJURY',
  BONE_FRACTURE = 'BONE_FRACTURE',
  HIP = "HIP"
}

/**
 * DTO for creating a new injury
 * Used with POST /api/v1/user/injuries
 */
export interface CreateInjuryStateDto {
  injuryIndex: number;  // 0.0 to 1.0 - severity of injury
  affectedArea: BodyPart | string;
  lastHealthyDate: string;  // ISO date string: YYYY-MM-DD
  lastInjuryDate?: string | null;  // ISO date string: YYYY-MM-DD
}

/**
 * DTO for updating an existing injury
 * Used with PUT /api/v1/user/injuries
 */
export interface UpdateInjuryDto {
  injuryId: number;
  injuryIndex: number;  // 0.0 to 1.0
  affectedArea: BodyPart | string;
  lastHealthyDate: string;  // ISO date string: YYYY-MM-DD
  lastInjuryDate?: string | null;  // ISO date string: YYYY-MM-DD
}

/**
 * DTO for viewing injury data
 * Returned by GET /api/v1/user/injuries
 */
export interface ViewInjuryDto {
  injuryId: number;
  injuryIndex: number;  // 0.0 to 1.0 - severity indicator
  affectedArea: BodyPart | string;
  lastHealthyDate: string;  // ISO date string: YYYY-MM-DD
  lastInjuryDate?: string | null;  // ISO date string: YYYY-MM-DD
}

/**
 * Helper interface for body part display information
 */
export interface BodyPartInfo {
  value: BodyPart | string;
  label: string;
  icon: string;
}

/**
 * Constant array of body parts with display information
 */
export const BODY_PARTS: BodyPartInfo[] = [
  {value: BodyPart.NECK_REGION, label: 'Neck', icon: 'body-outline'},
  {value: BodyPart.UPPER_REGION, label: 'Upper Body', icon: 'fitness-outline'},
  {value: BodyPart.CORE_REGION, label: 'Core', icon: 'ellipse-outline'},
  {value: BodyPart.UPPER_LEG_REGION, label: 'Upper Leg', icon: 'walk-outline'},
  {value: BodyPart.KNEE_REGION, label: 'Knee', icon: 'radio-button-on-outline'},
  {value: BodyPart.LOWER_LEG_REGION, label: 'Lower Leg', icon: 'walk-outline'},
  {value: BodyPart.FEET_REGION, label: 'Feet', icon: 'footsteps-outline'},
  {value: BodyPart.RESPIRATION_REGION, label: 'Respiratory', icon: 'heart-outline'},
  {value: BodyPart.SPINAL_INJURY, label: 'Spinal Injury', icon: 'warning-outline'},
  {value: BodyPart.BONE_FRACTURE, label: 'Bone Fracture', icon: 'alert-circle-outline'},
  {value: BodyPart.HIP, label: 'Hip', icon: 'body-outline'}
];

/**
 * Helper function to get body part label
 */
export function getBodyPartLabel(bodyPart: BodyPart | string): string {
  const part = BODY_PARTS.find(p => p.value === bodyPart);
  return part ? part.label : bodyPart;
}

/**
 * Helper function to convert injury index (0.0-1.0) to severity level
 */
export function getInjurySeverity(injuryIndex: number): 'MILD' | 'MODERATE' | 'SEVERE' {
  if (injuryIndex < 0.33) {
    return 'MILD';
  } else if (injuryIndex < 0.67) {
    return 'MODERATE';
  } else {
    return 'SEVERE';
  }
}

/**
 * Helper function to get severity color for Ionic based on injury index
 */
export function getSeverityColor(injuryIndex: number): string {
  if (injuryIndex < 0.33) {
    return 'success';  // Mild - green
  } else if (injuryIndex < 0.67) {
    return 'warning';  // Moderate - yellow/orange
  } else {
    return 'danger';   // Severe - red
  }
}

/**
 * Helper function to get severity label
 */
export function getSeverityLabel(injuryIndex: number): string {
  const severity = getInjurySeverity(injuryIndex);
  return severity.charAt(0) + severity.slice(1).toLowerCase();
}

/**
 * Helper function to calculate days since injury
 */
export function getDaysSinceInjury(lastInjuryDate: string): number {
  const injuryDate = new Date(lastInjuryDate);
  const today = new Date();
  const diffTime = Math.abs(today.getTime() - injuryDate.getTime());
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  return diffDays;
}

/**
 * Helper function to check if injury is recovered
 */
export function isRecovered(lastHealthyDate: string | null | undefined, lastInjuryDate: string): boolean {
  if (!lastHealthyDate) return false;
  return new Date(lastHealthyDate) > new Date(lastInjuryDate);
}
