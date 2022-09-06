import { Site } from '../../site/model';

export function createSites(count: number): Site[] {
  const sites: Site[] = [];
  let n = 1;

  while (n <= count) {
    const id = '00000000-0000-0000-0000-000000000' + n;
    const name = n % 2 == 0 ? `SITE-${n}` : `LOC-${n}`;

    sites.push({
      id,
      name,
    });
    n++;
  }

  return sites;
}
