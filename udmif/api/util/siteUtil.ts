import { Site } from '../src/site/model';

export const getCorrectDevicesCount = (site: Site): number => {
  return site.validation?.summary?.correct_devices?.length ?? 0;
};

export const getMissingDevicesCount = (site: Site): number => {
  return site.validation?.summary?.missing_devices?.length ?? 0;
};

export const getErrorDevicesCount = (site: Site): number => {
  return site.validation?.summary?.error_devices?.length ?? 0;
};

export const getTotalDevicesCount = (site: Site): number => {
  return getCorrectDevicesCount(site) + getMissingDevicesCount(site) + getErrorDevicesCount(site);
};
