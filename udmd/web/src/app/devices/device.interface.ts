export interface Device {
  id: string;
  name: string;
  make: string;
  model: string;
  site: string;
  section: string;
  lastPayload: Date;
  operational: Boolean;
  tags: string[];
}
