import React, { useState } from "react";
import { LegacyCard, Text, Divider, VerticalStack } from "@shopify/polaris";
import ParamsCard from "./ParamsCard";
import DeleteModal from "./DeleteModal";
import { HARDCODED, LOGIN_REQUEST, SAMPLE_DATA, TLS_AUTH } from "./TestRoleConstants";


const SavedParamComponent = ({
  getAuthWithCondList,
  setShowAuthComponent,
  setEditableDocs,
  saveAction,
  initialItems,
  setAuthMechanism,
  setOpenAuth,
  setAdvancedHeaderSettingsOpen
}) => {

  const [deletedIndex, setDeletedIndex] = useState(-1);
  const [showAuthDeleteModal, setShowAuthDeleteModal] = useState(false);


  const handleOpenEdit = (authObj, index) => {
    setAuthMechanism(authObj.authMechanism);
    const headerKVPairs = authObj.headerKVPairs || {};

    if (Object.keys(headerKVPairs).length > 0) {
      setAdvancedHeaderSettingsOpen(true);
    }
    setShowAuthComponent(true);
    if (authObj?.authMechanism?.type === "HardCoded") {
      setOpenAuth(HARDCODED)
  } else if (authObj?.authMechanism?.type === "LOGIN_REQUEST") {
      setOpenAuth(LOGIN_REQUEST)
  } else if (authObj?.authMechanism?.type === "TLS_AUTH") {
      setOpenAuth(TLS_AUTH)
  } else if (authObj?.authMechanism?.type === "SAMPLE_DATA") {
      setOpenAuth(SAMPLE_DATA)
  }
    setEditableDocs(index);
  };

  return getAuthWithCondList() &&
    getAuthWithCondList() !== undefined &&
    getAuthWithCondList().length > 0 ? (
    <LegacyCard
      title={<Text variant="headingMd">Configured auth details</Text>}
      key={"savedAuth"}
    >
      <br />
      <Divider />
      <LegacyCard.Section>
        <VerticalStack gap={6}>
          {getAuthWithCondList().map((authObj, index) => {
            return (
              <ParamsCard
                showEdit={() => handleOpenEdit(authObj, index)}
                dataObj={authObj}
                key={JSON.stringify(authObj)}
                handleDelete={() => {
                  setDeletedIndex(index);
                  setShowAuthDeleteModal(true);
                }}
              />
            );
          })}
        </VerticalStack>
      </LegacyCard.Section>
      <DeleteModal
        showAuthDeleteModal={showAuthDeleteModal}
        setShowAuthDeleteModal={setShowAuthDeleteModal}
        setDeletedIndex={setDeletedIndex}
        saveAction={saveAction}
        deletedIndex={deletedIndex}
        initialItems={initialItems}
      />
    </LegacyCard>
  ) : null;
};

export default SavedParamComponent;
