import React, { useEffect, useState } from 'react'
import Store from "../../../store"
import homeRequests from '../../home/api'
import OnboardingStore from '../OnboardingStore'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import func from '../../../../../util/func'

function CollectionSelection() {
    const apiCollections = Store(state => state.allCollections)
    const setCollections = Store(state => state.setAllCollections)
    const setSelectedCollection = OnboardingStore(state => state.setSelectedCollection)
    const collection = OnboardingStore(state => state.selectedCollection)
    
    const [loading, setLoading] = useState(true)
    const [dummyCollections, setDummyCollections] = useState([])

    const checkCollections = (apiCollections) => {
        const localCopy = apiCollections.filter((x) => x.displayName.toLowerCase() !== "default")
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
            if(apiCollections.length <= 1){
                await homeRequests.getCollections().then((resp)=> {
                    setCollections(resp.apiCollections)
                    localCopy = resp.apiCollections
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
        loading ? <SpinnerCentered /> :
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