import { Site } from '../../site/model';
import siteMessage from './siteValidationMessage.json';

export function createSites(count: number): Site[] {
  const sites: Site[] = [];
  let n = 1;

  while (n <= count) {
    const name = n % 2 == 0 ? `SITE-${n}` : `LOC-${n}`;
    const validation = siteMessage;

    sites.push({
      name,
      validation,
    });
    n++;
  }

  return sites;
}
