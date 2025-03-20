import React, { useEffect, useState } from 'react'
import homeRequests from '../../home/api'
import OnboardingStore from '../OnboardingStore'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import func from '../../../../../util/func'
import { Spinner } from '@shopify/polaris'
import PersistStore from '../../../../main/PersistStore'

function CollectionSelection() {
    const apiCollections = PersistStore(state => state.allCollections)
    const setCollections = PersistStore(state => state.setAllCollections)
    const setSelectedCollection = OnboardingStore(state => state.setSelectedCollection)
    const collection = OnboardingStore(state => state.selectedCollection)
    
    const [loading, setLoading] = useState(true)
    const [dummyCollections, setDummyCollections] = useState([])

    const checkCollections = (apiCollections) => {
        const allowedCollections = ['juice_shop_demo', 'vulnerable_apis']
        
        const localCopy = apiCollections.filter((x) => 
            allowedCollections.includes(x.displayName.toLowerCase())
        )
        setDummyCollections(localCopy)
        if(localCopy.length > 0){
            if(!collection){
                setSelectedCollection(localCopy[0].id)
            } 
            return true
        }
        return false
    }

    const stopFunc = (interval) => {
        setLoading(false)
        clearInterval(interval)
    }

    const getCollections = async()=> {
        let interval = setInterval(async () => {
            let localCopy = []
            if(apiCollections.length <= 1 && localCopy.length <= 1){
                await homeRequests.getCollections().then((resp)=> {
                    setCollections(resp.apiCollections)
                    localCopy = JSON.parse(JSON.stringify(resp.apiCollections));
                })
            }

            const useCollections = apiCollections.length > 0 ? apiCollections : localCopy

            if(checkCollections(useCollections)){
                stopFunc(interval)
            }
        },1000)
    }

    const mapCollectionIdToName = func.mapCollectionIdToName(dummyCollections)

    useEffect(()=> {
        getCollections()
    },[])

    const allCollectionsOptions = dummyCollections.map(collection => {
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