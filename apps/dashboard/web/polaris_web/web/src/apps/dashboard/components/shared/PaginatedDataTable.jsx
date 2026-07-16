import React, { useState } from "react";
import { Box, DataTable, HorizontalStack, Pagination } from "@shopify/polaris";

// DataTable has no built-in pagination prop, so this adds one.
const PaginatedDataTable = ({ columnContentTypes, headings, rows, rowsPerPage = 10 }) => {
  const [page, setPage] = useState(0);

  const totalPages = Math.max(1, Math.ceil(rows.length / rowsPerPage));
  // Clamped, not effect-reset: rows is a new reference every render.
  const currentPage = Math.min(page, totalPages - 1);
  const paged = rows.slice(currentPage * rowsPerPage, (currentPage + 1) * rowsPerPage);

  return (
    <>
      <DataTable
        columnContentTypes={columnContentTypes}
        headings={headings}
        rows={paged}
      />
      {/* Anchors to the nearest position:relative ancestor (InfoCard's component Box). */}
      <Box position="absolute" insetInlineStart="0" insetInlineEnd="0" insetBlockEnd="0">
        <HorizontalStack align="center">
          <Pagination
            hasNext={currentPage < totalPages - 1}
            hasPrevious={currentPage > 0}
            onNext={() => setPage(currentPage + 1)}
            onPrevious={() => setPage(currentPage - 1)}
          />
        </HorizontalStack>
      </Box>
    </>
  );
};

export default PaginatedDataTable;
