import { threatTimelineData } from './dummyData';

const mockThreatApi = {
    getThreatActivityTimeline: async (startTs, endTs) => {
        // Simulate API delay
        await new Promise(resolve => setTimeout(resolve, 300));

        // Return dummy data
        return threatTimelineData;
    }
};

export default mockThreatApi;
