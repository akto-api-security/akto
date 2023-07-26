import { RadioButton, Text } from '@shopify/polaris'
import React, { useState } from 'react'

function PostmanSource() {

    const [type, setType] = useState("api")
    const handleChange = (val) => {
        setType(val)
    }

    return (
        <div style={{marginTop: '20px', display: 'flex', flexDirection: 'column', gap: '20px'}}>
            <Text variant='bodyMd'>
                Use postman to send traffic to Akto and realize quick value. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
            </Text>

            <div style={{display: 'flex', flexDirection: 'column', gap: '4px'}}>
                <RadioButton id="api" label="Import using postman API key" checked={type === "api"} onChange={()=>handleChange("api")}/>
                <RadioButton id="collection" label="Import using postman collection file" checked={type === "collection"} onChange={()=>handleChange("collection")}/>
            </div>
        </div>
    )
}

export default PostmanSource