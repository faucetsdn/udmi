export interface PointModel {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export type Point = Partial<PointModel> | null;

export type PointsResponse = {
  points: Point[] | null;
};

export type PointsQueryResponse = PointsResponse;

export type PointsQueryVariables = {
  deviceId: string;
};
