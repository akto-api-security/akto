import { Page, LegacyCard, TextField, VerticalStack, Button } from '@shopify/polaris'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import PersistStore from '@/apps/main/PersistStore';

import defaultPayloadsApi from './api'
import func from '@/util/func'

import {useState, useEffect} from 'react'
function DefaultPayloads() {

    const hostNameMap = PersistStore(state => state.hostNameMap)
    const allHostnames = Object.keys(hostNameMap)


    const [selectedDomain, setSelectedDomain] = useState('')
    const [selectedDDefaultPayload, setSelectedDDefaultPayload] = useState('')
    const [patternText, setPatternText] = useState('')
    const [newCustomDomain, setNewCustomDomain] = useState('')
    const [allOptions, setAllOptions] = useState([{ label: "Add your own", value: "add-your-own" }])



    useEffect(()=>{
        async function combineDomainsFromPayloads() {
            let allDefaultPayloads = await defaultPayloadsApi.fetchAllDefaultPayloads()
            let allDefaultPayloadDomains = allDefaultPayloads.domains.map(x => x.id).filter(x => allHostnames.indexOf(x) == -1)
            console.log(allDefaultPayloadDomains)
            let allDefaultPayloadOptions =
                allDefaultPayloadDomains.map(x => {
                    return {
                        label: x,
                        value: x
                    }
                })

            setAllOptions([...allDefaultPayloadOptions, ...allHostnames, { label: "Add your own", value: "add-your-own" }]);
        }
        combineDomainsFromPayloads()
    },[])


    const handleSelectedDomain = async (selectedDomain) => {
        setSelectedDomain(selectedDomain)
        if (selectedDomain === 'add-your-own') {
            setNewCustomDomain('')
            setPatternText('')
        } else {
            let resp = await defaultPayloadsApi.fetchDefaultPayload(selectedDomain)
            setPatternText(resp.defaultPayload.pattern)
            setSelectedDDefaultPayload(resp.defaultPayload.pattern)
        }
    }

    function findSelectedDomainLabel() {
        let ret = allOptions.find(x => x.value === selectedDomain) || {label: "Select domain"}

        return ret.label
    }

    function isAddYourOwnDomain() {
        return (selectedDomain === 'add-your-own')
    }

    function subdomainFormat(str) {
        const tokens = str.split(".")
        return tokens.length >= 2 && tokens.filter(x => x.length == 0).length == 0

    }

    function isDomainSelected() {
        const isCustomDomain = isAddYourOwnDomain()
        const domain = (isCustomDomain ? newCustomDomain : selectedDomain)
        return subdomainFormat(domain)
    }

    const savePattern = async () => {
        let resp = await defaultPayloadsApi.saveDefaultPayload(newCustomDomain, patternText)
        let defaultPayload = resp.defaultPayload
        if (defaultPayload && defaultPayload.id === newCustomDomain) {
            allOptions.push({label: newCustomDomain, value: newCustomDomain})
            func.setToast(true, false, "Default config saved successfully")
            setSelectedDomain(defaultPayload.id)
        } else {
            func.setToast(true, true, "There was an error saving default payload")
        }
    }

    return (
        <Page title='Default payloads' divider>
            <LegacyCard>
                <LegacyCard.Section>
                    <VerticalStack gap={"4"}>
                        <DropdownSearch
                            id={"select-domain-name"}
                            placeholder="Select domain name"
                            optionsList={allOptions}
                            setSelected={handleSelectedDomain}
                            preSelected={allOptions[0].label}
                            value={findSelectedDomainLabel()}
                        />

                        {
                            isAddYourOwnDomain() &&
                            <TextField
                                 label="Enter domain"
                                 value={newCustomDomain}
                                 placeholder="For example, api.mydomain.com"
                                 onChange={(x) => {setNewCustomDomain(x)}}
                                 autoComplete="off"
                             />
                        }

                        {
                            isDomainSelected() &&
                            <TextField
                                label={"Enter matching pattern for " + newCustomDomain}
                                value={patternText}
                                placeholder="For example, <html>.*</html>"
                                onChange={(x) => {setPatternText(x)}}
                                autoComplete="off"
                            />
                        }
                        {
                            <div className='footer-save'>
                                <Button primary onClick={savePattern} disabled={patternText === '' || selectedDDefaultPayload === patternText}>Save</Button>
                            </div>
                        }
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>
        </Page>
    )
}

export default DefaultPayloads