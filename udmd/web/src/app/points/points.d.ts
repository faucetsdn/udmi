export interface PointModel {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export type Point = Partial<PointModel> | null;

export type PointsResponse = {
  device: {
    id: string;
    points: Point[];
  } | null;
};

export type PointsQueryResponse = PointsResponse;

export type PointsQueryVariables = {
  id: string;
};
