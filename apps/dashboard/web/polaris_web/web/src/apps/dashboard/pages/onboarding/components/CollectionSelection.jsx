import React, { useEffect, useState } from 'react'
import homeRequests from '../../home/api'
import OnboardingStore from '../OnboardingStore'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import func from '@/util/func'
import { Spinner } from '@shopify/polaris'

function CollectionSelection() {
    const [apiCollections, setApiCollections] = useState([])
    const setSelectedCollection = OnboardingStore(state => state.setSelectedCollection)
    const collection = OnboardingStore(state => state.selectedCollection)
    
    const [loading, setLoading] = useState(true)

    const getCollections = async()=> {
        if(collection === -1){
            setLoading(true)
            await homeRequests.getCollections().then((resp)=> {
                setApiCollections(resp?.apiCollections.filter(x => x.id !== 0))
            })
            setLoading(false)
        }
        
    }

    const mapCollectionIdToName = func.mapCollectionIdToName(apiCollections || [])

    useEffect(()=> {
        getCollections()
    },[])

    const allCollectionsOptions = apiCollections.length > 0 && apiCollections.map(collection => {
        return {
            label: collection.displayName,
            value: collection.id
        }
    })

    return (
        loading ? <div style={{margin: "auto"}}><Spinner size='small'/></div>:
        <DropdownSearch label="Select collection"
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={setSelectedCollection}
                    value={mapCollectionIdToName?.[collection]}
                    preSelected={[collection]}
        />
    )
}

export default CollectionSelection