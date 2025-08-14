import React, { useEffect, useState } from "react";
import { Card, DataTable, Pagination, Spinner, Button, TextField, Modal } from "@shopify/polaris";
import api from "./api";

const PAGE_SIZE = 10;

export default function AuditData() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [editIndex, setEditIndex] = useState(null);
  const [markedBy, setMarkedBy] = useState("");
  const [remarks, setRemarks] = useState("");
  const [modalActive, setModalActive] = useState(false);

  useEffect(() => {
    fetchData(page);
  }, [page]);

  const fetchData = async (pageNum) => {
    setLoading(true);
    const res = await api.fetchAuditData(pageNum, PAGE_SIZE);
    setData(res || []);
    setTotal((res && res.length) || 0);
    setLoading(false);
  };

  const handleEdit = (index) => {
    setEditIndex(index);
    setMarkedBy(data[index].markedBy || "");
    setRemarks(data[index].remarks || "");
    setModalActive(true);
  };

  const handleSave = async () => {
    const id = data[editIndex]?._id || data[editIndex]?.id;
    if (!id) return;
    await api.updateAuditData({
      id,
      markedBy,
      description: remarks
    });
    setModalActive(false);
    setEditIndex(null);
    fetchData(page);
  };

  const formatDate = (epoch) => {
    if (!epoch) return "";
    // Ensure epoch is a number and in milliseconds
    let epochNum = Number(epoch);
    if (epochNum < 1e12) {
      // If epoch is in seconds, convert to milliseconds
      epochNum *= 1000;
    }
    const date = new Date(epochNum);
    if (isNaN(date.getTime())) return "";
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
  };

  const rows = data.map((row, idx) => [
    formatDate(row.lastDetected),
    row.markedBy,
    row.type,
    row.remarks,
    <Button onClick={() => handleEdit(idx)} primary>Edit</Button>
  ]);

  return (
    <Card title="Audit Data">
      {loading ? (
        <Spinner size="large" />
      ) : (
        <>
          <DataTable
            columnContentTypes={["text", "text", "text", "text", "text"]}
            headings={["Last Detected", "Marked By", "Type", "Remarks", "Actions"]}
            rows={rows}
          />
          <Pagination
            hasPrevious={page > 1}
            onPrevious={() => setPage(page - 1)}
            hasNext={page * PAGE_SIZE < total}
            onNext={() => setPage(page + 1)}
          />
          <Modal
            open={modalActive}
            onClose={() => setModalActive(false)}
            title="Edit Audit Data"
            primaryAction={{
              content: "Save",
              onAction: handleSave
            }}
            secondaryActions={[{
              content: "Cancel",
              onAction: () => setModalActive(false)
            }]}
          >
            <Modal.Section>
              <TextField
                label="Marked By"
                value={markedBy}
                onChange={setMarkedBy}
                autoComplete="off"
              />
              <TextField
                label="Remarks"
                value={remarks}
                onChange={setRemarks}
                autoComplete="off"
                multiline
              />
            </Modal.Section>
          </Modal>
        </>
      )}
    </Card>
  );
}
