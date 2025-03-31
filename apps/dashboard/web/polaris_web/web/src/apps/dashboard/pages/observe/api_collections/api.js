import request from '@/util/request'

export default {
    deactivateCollections(items) {
        return request({
            url: '/api/deactivateCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },
    activateCollections(items) {
        return request({
            url: '/api/activateCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },
    fetchCountForHostnameDeactivatedCollections(){
        return request({
            url: '/api/getCountForHostnameDeactivatedCollections',
            method: 'post',
            data: {}
        })
    },
    getCollection(apiCollectionId){
        return  request({
            url: '/api/getCollection',
            method: 'post',
            data: {apiCollectionId}
        })
    },
    async updateCollectionDescription(apiCollectionId, description) {
        try {
            const response = await request({
                url: '/api/editCollection',
                method: 'post',
                data: { 
                    apiCollectionId: parseInt(apiCollectionId, 10),
                    description: description || ''
                }
            });

            if (!response || response.error) {
                throw new Error(response?.error || 'Failed to update description');
            }

            return response;
        } catch (error) {
            console.error('Error in updateCollectionDescription:', error);
            throw error;
        }
    }
}

export const updateCollectionDescription = async (collectionId, description) => {
    try {
        const response = await request({
            url: '/api/editCollection',
            method: 'post',
            data: {
                id: collectionId,
                description: description
            }
        });
        return response;
    } catch (error) {
        console.error('Error updating collection description:', error);
        throw error;
    }
};