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

  const onChangeQueryParams = (newParams) => {
    onChangeApiRequest(QUERY_PARAMS, newParams);
  }

  const onChangeRequestHeaders = (newHeaders) => {
    onChangeApiRequest(REQUEST_HEADERS, newHeaders);
  }

  const onChangeRequestPayload = (newPayload) => {
    onChangeApiRequest(REQUEST_PAYLOAD, newPayload);
  }

  return (
    <div style={{display: "flex"}}>
      <div style={{width: "400px"}}>
        <div className="request-title">[Request] Query params</div>
        <div className="request-editor request-editor-path">
          <TemplateStringEditor defaultText={updatedSampleData[QUERY_PARAMS] || sampleApiCall.path.indexOf("?") > -1 ?sampleApiCall.path.split("?")[1] : "-"} onChange={onChangeQueryParams}/>
        </div>
        <div className="request-title">[Request] Headers</div>
        <div className="request-editor request-editor-headers">
          {<TemplateStringEditor defaultText={updatedSampleData[REQUEST_HEADERS] || sampleApiCall.requestHeaders} onChange={onChangeRequestHeaders}/>}
        </div>
        <div className="request-title">[Request] Payload</div>
        <div className="request-editor request-editor-payload">
          <TemplateStringEditor defaultText={updatedSampleData[REQUEST_PAYLOAD] || sampleApiCall.requestPayload} onChange={onChangeRequestPayload}/>
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
        updateSampleData(json);
      }
      getSampleData();
    }, 
    [endpointDetails]
  );

  const addNodeEndpoint = useStore(state => state.addNodeEndpoint)
  const nodeEndpointMap = useStore(state => state.nodeEndpointMap)

  const onChangeApiRequest = (key, newData) => {
    let currNewSampleData = {...newSampleData}
    currNewSampleData[key] = newData
    setNewSampleData(currNewSampleData)
  }

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    let updatedSampleData = {...endpointDetails.updatedSampleData, ...newSampleData}
    addNodeEndpoint(nodeId, {...endpointDetails, updatedSampleData})
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
            { 
              sampleData && 
              sampleData.sampleDataList && 
              sampleData.sampleDataList[0] && 
              sampleData.sampleDataList[0].samples && 
              sampleData.sampleDataList[0].samples[0] && 
              <RequestEditor sampleApiCall={JSON.parse(sampleData.sampleDataList[0].samples[0])} updatedSampleData={nodeEndpointMap[nodeId].updatedSampleData} onChangeApiRequest={onChangeApiRequest}/>
            }
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} variant="contained" style={{textTransform: "unset"}}>Save</Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}