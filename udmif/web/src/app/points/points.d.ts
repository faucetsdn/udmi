export interface PointModel {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export type Point = Partial<PointModel>;

export type PointsQueryResponse = {
  points?: Point[];
};

export type PointsQueryVariables = {
  deviceId: string;
};
