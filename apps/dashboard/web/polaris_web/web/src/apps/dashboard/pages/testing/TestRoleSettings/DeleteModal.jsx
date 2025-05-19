import React from "react";
import api from "../api";
import func from "@/util/func";
import { Modal, Text } from "@shopify/polaris";

const DeleteModal = ({
  showAuthDeleteModal,
  setShowAuthDeleteModal,
  setDeletedIndex,
  saveAction,
  deletedIndex,
  initialItems,
}) => {
  const handleDeleteAuth = async () => {
    const resp = await api.deleteAuthFromRole(initialItems.name, deletedIndex);
    setShowAuthDeleteModal(false);
    setDeletedIndex(-1);
    await saveAction(true, resp.selectedRole.authWithCondList);
    func.setToast(
      true,
      false,
      "Auth mechanism removed from role successfully."
    );
  };

  return (
    <Modal
      open={showAuthDeleteModal}
      onClose={() => {
        setShowAuthDeleteModal(false);
        setDeletedIndex(-1);
      }}
      title="Are you sure?"
      primaryAction={{
        content: "Delete auth mechanism",
        onAction: handleDeleteAuth,
      }}
    >
      <Modal.Section>
        <Text variant="bodyMd">
          Are you sure you want to this auth mechanism.
        </Text>
      </Modal.Section>
    </Modal>
  );
};

export default DeleteModal;
