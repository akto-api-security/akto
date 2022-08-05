import * as React from 'react';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit } from '@fortawesome/free-regular-svg-icons'

import TemplateStringEditor from './TemplateStringEditor.jsx';
import useStore from './store'

import './start-node.css'

const RequestEditor = ({sampleApiCall, updatedSampleData, onChangeApiRequest}) => {
  let QUERY_PARAMS = "queryParams"
  let REQUEST_HEADERS = "requestHeaders"
  let REQUEST_PAYLOAD = "requestPayload"
  let REQUEST_URL = "requestUrl"


  let oldParams = sampleApiCall.path.indexOf("?") > -1 ?sampleApiCall.path.split("?")[1] : "";
  let oldHeaders = sampleApiCall.requestHeaders
  let oldPayload = sampleApiCall.requestPayload
  let oldUrl = sampleApiCall.path.split("?")[0]

  const onChangeQueryParams = (newParams) => {
    onChangeApiRequest(QUERY_PARAMS, newParams);
  }

  const onChangeRequestHeaders = (newHeaders) => {
    onChangeApiRequest(REQUEST_HEADERS, newHeaders);
  }

  const onChangeRequestPayload = (newPayload) => {
    onChangeApiRequest(REQUEST_PAYLOAD, newPayload);
  }

  const onChangeRequestUrl = (newUrl) => {
    onChangeApiRequest(REQUEST_URL, newUrl);
  }

  return (
    <div style={{display: "flex"}}>
      <div style={{width: "400px"}}>
        <div className="request-title">[Request] URL</div>
        <div className="request-editor request-editor-path">
          <TemplateStringEditor defaultText={updatedSampleData[REQUEST_URL] || oldUrl} onChange={onChangeRequestUrl}/>
        </div>
        <div className="request-title">[Request] Query params</div>
        <div className="request-editor request-editor-path">
          <TemplateStringEditor defaultText={updatedSampleData[QUERY_PARAMS] || oldParams} onChange={onChangeQueryParams}/>
        </div>
        <div className="request-title">[Request] Headers</div>
        <div className="request-editor request-editor-headers">
          {<TemplateStringEditor defaultText={updatedSampleData[REQUEST_HEADERS] || oldHeaders} onChange={onChangeRequestHeaders}/>}
        </div>
        <div className="request-title">[Request] Payload</div>
        <div className="request-editor request-editor-payload">
          <TemplateStringEditor defaultText={updatedSampleData[REQUEST_PAYLOAD] || oldPayload} onChange={onChangeRequestPayload}/>
        </div>
      </div>
      <div style={{width: "400px", opacity: "0.5"}}>
        <div className="request-title">[Response] Headers</div>
        <div className="request-editor request-editor-headers">
          {sampleApiCall.responseHeaders}
        </div>
        <div className="request-title">[Response] Payload</div>
        <div className="request-editor request-editor-payload">
          {sampleApiCall.responsePayload}
        </div>
      </div>
    </div>
  )
}

export default function InputArgumentsDialog({nodeId, endpointDetails, fetchSampleDataFunc}) {

  const [open, setOpen] = React.useState(false);
  const [newSampleData, setNewSampleData] = React.useState({})
  const [sampleData, updateSampleData] = React.useState({});
  React.useEffect(() => {
      const getSampleData = async () => {
        const json = await fetchSampleDataFunc(endpointDetails.endpoint, endpointDetails.apiCollectionId, endpointDetails.method)
               
        updateSampleData(json && 
          json.sampleDataList && 
          json.sampleDataList[0] && 
          json.sampleDataList[0].samples && 
          json.sampleDataList[0].samples[0] && JSON.parse(json.sampleDataList[0].samples[0]) || {});
      }
      getSampleData();
    }, 
    [endpointDetails]
  );

  const addNodeEndpoint = useStore(state => state.addNodeEndpoint)
  const nodeEndpointMap = useStore(state => state.nodeEndpointMap)

  const onChangeApiRequest = (key, newData) => {
    let currNewSampleData = {orig: JSON.stringify(sampleData)}
    currNewSampleData[key] = newData
    let updatedSampleData = {...nodeEndpointMap[nodeId].updatedSampleData, ...currNewSampleData}
    addNodeEndpoint(nodeId, {...endpointDetails, updatedSampleData})
  }

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <div>
      <div style={{float: "right"}}>
        <IconButton onClick={handleClickOpen}>
          <FontAwesomeIcon icon={faEdit} className="primary-btn" />
        </IconButton>
      </div>
      <Dialog open={open} onClose={handleClose} className="input-arguments-dialog" style={{minWidth: "850px"}}>
        <div className="request-title"></div>
        <DialogContent>
            
          <RequestEditor sampleApiCall={sampleData} updatedSampleData={nodeEndpointMap[nodeId].updatedSampleData} onChangeApiRequest={onChangeApiRequest}/>
            
        </DialogContent>
      </Dialog>
    </div>
  );
}