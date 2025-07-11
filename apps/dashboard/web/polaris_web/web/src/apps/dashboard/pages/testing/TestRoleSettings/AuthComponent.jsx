import React, { useState } from "react";
import {
  Box,
  Button,
  FormLayout, 
  LegacyCard, 
  HorizontalStack,
  LegacyStack,
  VerticalStack,
  Text,
  Collapsible,
  TextField,
  Tooltip,
  Icon,
} from "@shopify/polaris";
import api from '../api';
import func from "@/util/func";
import { ChevronRightMinor, ChevronDownMinor, InfoMinor } from '@shopify/polaris-icons';
import LoginStepBuilder from '../user_config/LoginStepBuilder';
import HardCoded from '../user_config/HardCoded';
import JsonRecording from '../user_config/JsonRecording';
import Dropdown from '../../../components/layouts/Dropdown';
import TlsAuth from '../user_config/TlsAuth';
import SampleDataAuth from '../user_config/SampleDataAuth';
import { HARDCODED, LOGIN_REQUEST, SAMPLE_DATA, TLS_AUTH } from "./TestRoleConstants"; 



const AuthComponent = ({
  showAuthComponent,
  setShowAuthComponent,
  hardcodedOpen,
  setHardcodedOpen,
  setAuthMechanism,
  editableDoc,
  setEditableDocs,
  initialItems,
  saveAction,
  isNew,
  openAuth,
  setOpenAuth,
  advancedHeaderSettingsOpen,
  setAdvancedHeaderSettingsOpen
}) => {


  const [currentInfo, setCurrentInfo] = useState({ steps: [], authParams: {} });
  const [headerKey, setHeaderKey] = useState("");
  const [headerValue, setHeaderValue] = useState("");
  const [automationType, setAutomationType] = useState("LOGIN_STEP_BUILDER");
  const [hardCodeAuthInfo, setHardCodeAuthInfo] = useState({ authParams: [] });
  const [sampleDataAuthInfo, setSampleDataAuthInfo] = useState({ authParams: [] });
  const [tlsAuthInfo, setTlsAuthInfo] = useState({authParams:[]})

  const automationOptions = [
    { label: "Login Step Builder", value: "LOGIN_STEP_BUILDER" },
    { label: "JSON Recording", value: "RECORDED_FLOW" },
  ];

  const setTlsInfo = (obj) => {
    setTlsAuthInfo(prev => ({
        ...prev,
        authParams: obj.authParams
    }))
}

  const handleLoginInfo = (obj) => {
    setCurrentInfo((prev) => ({
      ...prev,
      steps: obj.steps,
      authParams: obj.authParams,
    }));
  };

  const handleSelectAutomationType = async (type) => {
    setAutomationType(type);
  };

  const setHardCodedInfo = (obj) => {
    setHardCodeAuthInfo((prev) => ({
      ...prev,
      authParams: obj.authParams,
    }));
  };

  const setSampleDataInfo = (obj) => {
    setSampleDataAuthInfo((prev) => ({
      ...prev,
      authParams: obj.authParams,
    }));
  };

  const handleCancel = () => {

    setShowAuthComponent(false);
    setCurrentInfo({});
    setHeaderKey("");
    setHeaderValue("");
    setHardCodeAuthInfo({ authParams: [] });
    setSampleDataAuthInfo ({authParams: []});
    setAuthMechanism(null);
    setHardcodedOpen(true);
    setEditableDocs(-1);
    setTlsAuthInfo({authParams:[]})
    setOpenAuth(HARDCODED)
  };

  function checkOpenAuth(type) {
      return openAuth === type;
  }

  const handleSaveAuthMechanism = async () => {
    const apiCond = { [headerKey]: headerValue };
    let resp = {};
    if (openAuth === HARDCODED) {
      const currentAutomationType = "HardCoded";
      const authParamData = hardCodeAuthInfo.authParams;
      if (editableDoc > -1) {
        resp = await api.updateAuthInRole(
          initialItems.name,
          apiCond,
          editableDoc,
          authParamData,
          currentAutomationType
        );
      } else {
        resp = await api.addAuthToRole(
          initialItems.name,
          apiCond,
          authParamData,
          currentAutomationType,
          null
        );
      }
    } else if (openAuth === LOGIN_REQUEST) {
      const currentAutomationType = "LOGIN_REQUEST";

      let recordedLoginFlowInput = null;
      if (currentInfo.steps && currentInfo.steps.length > 0) {
        if (currentInfo.steps[0].type === "RECORDED_FLOW") {
          recordedLoginFlowInput = {
            content: currentInfo.steps[0].content,
            tokenFetchCommand: currentInfo.steps[0].tokenFetchCommand,
            outputFilePath: null,
            errorFilePath: null,
          };
        }

        if (editableDoc > -1) {
          resp = await api.updateAuthInRole(
            initialItems.name,
            apiCond,
            editableDoc,
            currentInfo.authParams,
            currentAutomationType,
            currentInfo.steps,
            recordedLoginFlowInput
          );
        } else {
          resp = await api.addAuthToRole(
            initialItems.name,
            apiCond,
            currentInfo.authParams,
            currentAutomationType,
            currentInfo.steps,
            recordedLoginFlowInput
          );
        }
      } else {
        func.setToast(true, true, "Request data cannot be empty!");
      }
    } else if (openAuth === TLS_AUTH) {
        const currentAutomationType = TLS_AUTH;
        const authParamData = tlsAuthInfo.authParams
        if(editableDoc > -1){
            resp = await api.updateAuthInRole(initialItems.name, apiCond, editableDoc, authParamData, currentAutomationType)
        }else{
            resp = await api.addAuthToRole(initialItems.name, apiCond, authParamData, currentAutomationType, null)
        }
    } else if (openAuth == SAMPLE_DATA) {
        const currentAutomationType = SAMPLE_DATA;
        const authParamData = sampleDataAuthInfo.authParams;
        if (editableDoc > -1) {
          resp = await api.updateAuthInRole(
            initialItems.name,
            apiCond,
            editableDoc,
            authParamData,
            currentAutomationType
          );
        } else {
          resp = await api.addAuthToRole(
            initialItems.name,
            apiCond,
            authParamData,
            currentAutomationType,
            null
          );
        }

    }
    handleCancel();
    await saveAction(true, resp.selectedRole.authWithCondList);
    func.setToast(true, false, "Auth mechanism added to role successfully.");
  };

  return showAuthComponent ? (
    <LegacyCard
      title="Authentication details"
      key="auth"
      secondaryFooterActions={[
        { content: "Cancel", destructive: true, onAction: handleCancel },
      ]}
      primaryFooterAction={{
        content: <div data-testid="save_token_details_button">Save</div>,
        onAction: handleSaveAuthMechanism,
      }}
    >
      <LegacyCard.Section title="Token details">
        <LegacyStack vertical>
          <Button
            id={"hardcoded-token-expand-button"}
            onClick={() => setOpenAuth(HARDCODED)}
            ariaExpanded={checkOpenAuth(HARDCODED)}
            icon={checkOpenAuth(HARDCODED) ? ChevronDownMinor : ChevronRightMinor}
            ariaControls="hardcoded"
          >
            Hard coded
          </Button>
          <Collapsible
            open={checkOpenAuth(HARDCODED)}
            id="hardcoded"
            transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
            expandOnPrint
          >
            <HardCoded
              showOnlyApi={true}
              extractInformation={true}
              setInformation={setHardCodedInfo}
            />
          </Collapsible>
        </LegacyStack>

        <LegacyStack vertical>
          <Button
            id={"sample-data-token-expand-button"}
            onClick={() => setOpenAuth(SAMPLE_DATA)}
            ariaExpanded={checkOpenAuth(SAMPLE_DATA)}
            icon={checkOpenAuth(SAMPLE_DATA) ? ChevronDownMinor : ChevronRightMinor}
            ariaControls="sample-data"
          >
            From traffic
          </Button>
          <Collapsible
            open={checkOpenAuth(SAMPLE_DATA)}
            id="sample-data"
            transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
            expandOnPrint
          >
            <SampleDataAuth
              showOnlyApi={true}
              extractInformation={true}
              setInformation={setSampleDataInfo}
            />
          </Collapsible>
        </LegacyStack>

        <LegacyStack vertical>
          <Button
            id={"automated-token-expand-button"}
            onClick={() => setOpenAuth(LOGIN_REQUEST)}
            ariaExpanded={checkOpenAuth(LOGIN_REQUEST)}
            icon={checkOpenAuth(LOGIN_REQUEST) ? ChevronDownMinor : ChevronRightMinor}
            ariaControls="automated"
          >
            Automated
          </Button>
          <Collapsible
            open={checkOpenAuth(LOGIN_REQUEST)}
            id="automated"
            transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
            expandOnPrint
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "max-content max-content",
                gap: "10px",
                alignItems: "center",
              }}
            >
              <Text>Select automation type:</Text>
              <Dropdown
                selected={handleSelectAutomationType}
                menuItems={automationOptions}
                initial={automationType}
              />
            </div>
            <br />

            {automationType === "LOGIN_STEP_BUILDER" && (
              <LoginStepBuilder
                extractInformation={true}
                showOnlyApi={true}
                setStoreData={handleLoginInfo}
              />
            )}
            {automationType === "RECORDED_FLOW" && (
              <JsonRecording
                extractInformation={true}
                showOnlyApi={true}
                setStoreData={handleLoginInfo}
              />
            )}
          </Collapsible>
        </LegacyStack>
        <LegacyStack vertical>
          <Button
              id={"tls-token-expand-button"}
              onClick={() => setOpenAuth(TLS_AUTH)}
              ariaExpanded={checkOpenAuth(TLS_AUTH)}
              icon={checkOpenAuth(TLS_AUTH) ? ChevronDownMinor : ChevronRightMinor}
              ariaControls="TLS_AUTH"
          >
              TLS Authentication
          </Button>
          <Collapsible
              open={checkOpenAuth(TLS_AUTH)}
              id="TLS_AUTH"
              transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}
              expandOnPrint
          >
              <TlsAuth setInformation={setTlsInfo}/>
          </Collapsible>
      </LegacyStack>
      </LegacyCard.Section>
      <LegacyCard.Section title="More settings">
        <VerticalStack gap={"2"}>
          <Box>
            <Button
              disclosure={advancedHeaderSettingsOpen ? "up" : "down"}
              onClick={() =>
                setAdvancedHeaderSettingsOpen(!advancedHeaderSettingsOpen)
              }
            >
              Advanced Settings
            </Button>
          </Box>
          <Collapsible
            open={advancedHeaderSettingsOpen}
            transition={{ duration: "300ms", timingFunction: "ease-in-out" }}
          >
            <VerticalStack gap={"2"}>
              <Text variant="headingMd">Api header conditions</Text>
              <FormLayout>
                <FormLayout.Group>
                  <TextField
                    id={"auth-header-key-field"}
                    label={
                      <HorizontalStack gap="2">
                        <Text>Header key</Text>
                        <Tooltip
                          content="Please enter name of the header which contains your auth token. This field is case-sensitive. eg Authorization"
                          dismissOnMouseOut
                          width="wide"
                          preferredPosition="below"
                        >
                          <Icon source={InfoMinor} color="base" />
                        </Tooltip>
                      </HorizontalStack>
                    }
                    value={headerKey}
                    onChange={setHeaderKey}
                  />
                  <TextField
                    id={"auth-header-value-field"}
                    label={
                      <HorizontalStack gap="2">
                        <Text>Header value</Text>
                        <Tooltip
                          content="Please enter the value of the auth token."
                          dismissOnMouseOut
                          width="wide"
                          preferredPosition="below"
                        >
                          <Icon source={InfoMinor} color="base" />
                        </Tooltip>
                      </HorizontalStack>
                    }
                    value={headerValue}
                    onChange={setHeaderValue}
                  />
                </FormLayout.Group>
              </FormLayout>
            </VerticalStack>
          </Collapsible>
        </VerticalStack>
      </LegacyCard.Section>
    </LegacyCard>
  ) : (
    <HorizontalStack align="end" key="auth-button">
      {isNew ? (
        <Tooltip content="Save the role first">
          <Button disabled>Add auth</Button>
        </Tooltip>
      ) : (
        <Button primary onClick={() => setShowAuthComponent(true)}>
          <div data-testid="add_auth_button">Add auth</div>
        </Button>
      )}
    </HorizontalStack>
  );
};

export default AuthComponent;
