import { logger } from "../common/logger";

export const resolvers = {
  Query: {
    devices: (_, {}, { dataSources: { } }) => {
      return [
        { id: "id1", name:"name1" }, 
        { id: "id2", name:"name2" }, 
        { id: "id2", name:"name3" }
      ];
    },
  },
};
