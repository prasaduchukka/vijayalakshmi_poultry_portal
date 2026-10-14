package com.vpf.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.vpf.dto.*;
import com.vpf.entity.Customer;
import com.vpf.entity.Supplier;
import com.vpf.repository.CustomerPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the five printable PDF reports required by the spec, using OpenPDF
 * (no external service, pure Java, works offline on any host).
 */
@Service
@RequiredArgsConstructor
public class PdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(31, 77, 46));
    private static final Font SUB_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Color HEADER_BG = new Color(31, 77, 46);

    private final CustomerPaymentRepository customerPaymentRepository;
    private final DeliveryService deliveryService;
    private final FarmSettingsService farmSettingsService;

    // ---------------------------------------------------------------
    // Customer Statement PDF - mirrors the owner's paper ledger format:
    // Date | Birds | Kg | Rate | Amount | O.Bal | T.Bal | Paid | Balance
    // ---------------------------------------------------------------
    public byte[] customerStatement(Customer customer, LocalDate from, LocalDate to,
                                     List<DeliveryResponse> deliveries,
                                     List<CustomerPaymentResponse> payments,
                                     List<FeedSaleResponse> feedSales,
                                     java.math.BigDecimal openingBalance) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Customer Statement"));
        doc.add(new Paragraph(customer.getChickenCenterName(), SUB_FONT));
        if (customer.getPhoneNumber() != null) doc.add(new Paragraph("Phone: " + customer.getPhoneNumber(), NORMAL_FONT));
        doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), NORMAL_FONT));
        doc.add(Chunk.NEWLINE);

        writeCustomerLedgerTable(doc, deliveries, payments, feedSales, openingBalance);

        doc.close();
        return out.toByteArray();
    }

    /** Shared by the single-customer statement and the all-customers combined report. */
    private void writeCustomerLedgerTable(Document doc, List<DeliveryResponse> deliveries,
                                           List<CustomerPaymentResponse> payments,
                                           List<FeedSaleResponse> feedSales,
                                           java.math.BigDecimal openingBalance) throws DocumentException {
        // Merge deliveries + feed sales + payments into one chronological ledger, like the paper record.
        record Row(LocalDate date, int kind, DeliveryResponse d, CustomerPaymentResponse p, FeedSaleResponse f) {}
        List<Row> rows = new java.util.ArrayList<>();
        for (DeliveryResponse d : deliveries) rows.add(new Row(d.getDeliveryDate(), 1, d, null, null));
        for (CustomerPaymentResponse p : payments) rows.add(new Row(p.getPaymentDate(), 2, null, p, null));
        if (feedSales != null) for (FeedSaleResponse f : feedSales) rows.add(new Row(f.getSaleDate(), 3, null, null, f));
        rows.sort(java.util.Comparator.comparing(Row::date));

        PdfPTable table = new PdfPTable(new float[]{1, 0.7f, 0.9f, 0.7f, 1, 1, 1, 1, 1, 1.4f});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Date", "Birds", "Kg", "Rate", "Amount", "O.Bal", "T.Bal", "Paid", "Balance", "Description");

        java.math.BigDecimal running = openingBalance;
        java.math.BigDecimal totalSales = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
        for (Row row : rows) {
            java.math.BigDecimal oBal = running;
            if (row.kind() == 1) {
                DeliveryResponse d = row.d();
                java.math.BigDecimal amount = d.getSalesAmount() == null ? java.math.BigDecimal.ZERO : d.getSalesAmount();
                java.math.BigDecimal tBal = oBal.add(amount);
                running = tBal;
                totalSales = totalSales.add(d.getSalesAmount() == null ? java.math.BigDecimal.ZERO : d.getSalesAmount());
                table.addCell(cell(d.getDeliveryDate().format(DATE_FMT)));
                table.addCell(cell(d.getNumberOfBirds() != null ? String.valueOf(d.getNumberOfBirds()) : "-"));
                table.addCell(cell((d.getReceivedWeight() != null ? d.getReceivedWeight() : d.getDispatchWeight()) + ""));
                table.addCell(cell(d.getSellingRate() + ""));
                table.addCell(cell(d.getSalesAmount() + ""));
                table.addCell(cell(oBal + ""));
                table.addCell(cell(tBal + ""));
                table.addCell(cell("-"));
                table.addCell(cell(running + ""));
                table.addCell(cell(d.getNotes() != null ? d.getNotes() : "-"));
            } else if (row.kind() == 3) {
                FeedSaleResponse f = row.f();
                java.math.BigDecimal amount = f.getAmount() == null ? java.math.BigDecimal.ZERO : f.getAmount();
                java.math.BigDecimal tBal = oBal.add(amount);
                running = tBal;
                totalSales = totalSales.add(f.getAmount() == null ? java.math.BigDecimal.ZERO : f.getAmount());
                table.addCell(cell(f.getSaleDate().format(DATE_FMT)));
                table.addCell(cell("-"));
                table.addCell(cell("Feed"));
                table.addCell(cell("-"));
                table.addCell(cell(f.getAmount() + ""));
                table.addCell(cell(oBal + ""));
                table.addCell(cell(tBal + ""));
                table.addCell(cell("-"));
                table.addCell(cell(running + ""));
                table.addCell(cell(f.getDescription() != null ? f.getDescription() : "Feed sale"));
            } else {
                CustomerPaymentResponse p = row.p();
                running = oBal.subtract(p.getAmount() == null ? java.math.BigDecimal.ZERO : p.getAmount());
                totalPaid = totalPaid.add(p.getAmount() == null ? java.math.BigDecimal.ZERO : p.getAmount());
                table.addCell(cell(p.getPaymentDate().format(DATE_FMT)));
                table.addCell(cell("-"));
                table.addCell(cell("-"));
                table.addCell(cell("-"));
                table.addCell(cell("-"));
                table.addCell(cell(oBal + ""));
                table.addCell(cell(oBal + ""));
                table.addCell(cell(rupees(p.getAmount())));
                table.addCell(cell(running + ""));
                String desc = p.getPaymentMethod() + (p.getReferenceNumber() != null ? " \u00b7 Ref: " + p.getReferenceNumber() : "");
                table.addCell(cell(desc));
            }
        }
        doc.add(table);
        doc.add(Chunk.NEWLINE);

        doc.add(totalsBlock(
                "Opening Balance", rupees(openingBalance),
                "Total Sales", rupees(totalSales),
                "Total Paid", rupees(totalPaid),
                "Closing Balance", rupees(running)));
    }

    /** All customers, one combined PDF - each customer's statement starts on a new page. */
    public byte[] allCustomerStatements(List<Customer> customers, LocalDate from, LocalDate to,
                                         java.util.Map<Long, List<DeliveryResponse>> deliveriesByCustomer,
                                         java.util.Map<Long, List<CustomerPaymentResponse>> paymentsByCustomer,
                                         java.util.Map<Long, List<FeedSaleResponse>> feedSalesByCustomer) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        boolean first = true;
        for (Customer customer : customers) {
            if (!first) doc.newPage();
            first = false;

            addLetterhead(doc);
            doc.add(sectionTitle("Customer Statement"));
            doc.add(new Paragraph(customer.getChickenCenterName(), SUB_FONT));
            if (customer.getPhoneNumber() != null) doc.add(new Paragraph("Phone: " + customer.getPhoneNumber(), NORMAL_FONT));
            doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), NORMAL_FONT));
            doc.add(Chunk.NEWLINE);

            writeCustomerLedgerTable(doc,
                    deliveriesByCustomer.getOrDefault(customer.getId(), List.of()),
                    paymentsByCustomer.getOrDefault(customer.getId(), List.of()),
                    feedSalesByCustomer.getOrDefault(customer.getId(), List.of()),
                    customer.getOpeningBalance());
        }

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------
    // Supplier Purchase PDF
    // ---------------------------------------------------------------
    public byte[] supplierPurchase(Supplier supplier, List<PurchaseResponse> purchases,
                                    List<SupplierPaymentResponse> payments,
                                    java.math.BigDecimal outstanding) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Supplier Purchase Report"));
        doc.add(new Paragraph(supplier.getSupplierName(), SUB_FONT));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1.3f, 1, 1, 1, 1.2f, 1.2f, 1.4f});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Date", "Purchase ID", "Birds", "Boxes", "Weight", "Rate", "Amount");
        java.math.BigDecimal totalPurchased = java.math.BigDecimal.ZERO;
        for (PurchaseResponse p : purchases) {
            table.addCell(cell(p.getPurchaseDate().format(DATE_FMT)));
            table.addCell(cell(String.valueOf(p.getId())));
            table.addCell(cell(p.getNumberOfBirds() != null ? String.valueOf(p.getNumberOfBirds()) : "-"));
            table.addCell(cell(p.getNumberOfBoxes() != null ? String.valueOf(p.getNumberOfBoxes()) : "-"));
            table.addCell(cell(p.getPurchaseWeight() + " kg"));
            table.addCell(cell(rupees(p.getPurchaseRate())));
            table.addCell(cell(rupees(p.getPurchaseAmount())));
            totalPurchased = totalPurchased.add(p.getPurchaseAmount() == null ? java.math.BigDecimal.ZERO : p.getPurchaseAmount());
        }
        doc.add(table);
        doc.add(Chunk.NEWLINE);

        doc.add(sectionTitle("Payments"));
        PdfPTable payTable = new PdfPTable(new float[]{1.5f, 1.2f, 1.3f, 2});
        payTable.setWidthPercentage(100);
        addHeaderRow(payTable, "Date", "Amount", "Method", "Reference");
        java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
        for (SupplierPaymentResponse p : payments) {
            payTable.addCell(cell(p.getPaymentDate().format(DATE_FMT)));
            payTable.addCell(cell(rupees(p.getAmount())));
            payTable.addCell(cell(p.getPaymentMethod().name()));
            payTable.addCell(cell(p.getReferenceNumber() != null ? p.getReferenceNumber() : "-"));
            totalPaid = totalPaid.add(p.getAmount() == null ? java.math.BigDecimal.ZERO : p.getAmount());
        }
        doc.add(payTable);
        doc.add(Chunk.NEWLINE);

        doc.add(totalsBlock(
                "Total Purchased", rupees(totalPurchased),
                "Total Paid", rupees(totalPaid),
                "Outstanding", rupees(outstanding)));

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------
    // Daily Sales PDF
    // ---------------------------------------------------------------
    public byte[] dailySales(LocalDate date, List<DeliveryResponse> deliveries) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Daily Sales Report"));
        doc.add(new Paragraph("Date: " + date.format(DATE_FMT), NORMAL_FONT));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{2, 0.8f, 1.2f, 1.2f, 1.2f, 1, 1.3f});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Customer", "Boxes", "Dispatch Wt", "Received Wt", "Wt Diff", "Rate", "Amount");
        java.math.BigDecimal totalSales = java.math.BigDecimal.ZERO;
        for (DeliveryResponse d : deliveries) {
            table.addCell(cell(d.getCustomerName()));
            table.addCell(cell(String.valueOf(d.getNumberOfBoxes())));
            table.addCell(cell(d.getDispatchWeight() + " kg"));
            table.addCell(cell(d.getReceivedWeight() + " kg"));
            table.addCell(cell(d.getWeightDifference() + " kg"));
            table.addCell(cell(rupees(d.getSellingRate())));
            table.addCell(cell(rupees(d.getSalesAmount())));
            totalSales = totalSales.add(d.getSalesAmount() == null ? java.math.BigDecimal.ZERO : d.getSalesAmount());
        }
        doc.add(table);
        doc.add(Chunk.NEWLINE);
        doc.add(totalsBlock("Total Sales", rupees(totalSales)));

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------
    // Vehicle Trip Report - lets the owner cross-check the supplier's
    // dispatch weight against what actually got delivered across all stops.
    // ---------------------------------------------------------------
    public byte[] vehicleTripReport(String vehicleNumber, LocalDate from, LocalDate to,
                                     List<TripResponse> trips,
                                     java.util.Map<Long, List<DeliveryResponse>> deliveriesByTrip,
                                     java.util.Map<Long, List<PurchaseResponse>> purchasesByTrip) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Vehicle Trip Report"));
        doc.add(new Paragraph("Vehicle: " + vehicleNumber, SUB_FONT));
        doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), NORMAL_FONT));
        doc.add(Chunk.NEWLINE);

        for (TripResponse trip : trips) {
            doc.add(sectionTitle(trip.getTripDate().format(DATE_FMT) +
                    (trip.getTripNumber() != null ? " \u2013 Trip " + trip.getTripNumber() : "") +
                    (trip.getCompanyName() != null ? " \u2013 " + trip.getCompanyName() : "")));

            PdfPTable infoTable = new PdfPTable(new float[]{1, 1, 1, 1});
            infoTable.setWidthPercentage(100);
            infoTable.addCell(labelCell("Driver"));
            infoTable.addCell(valueCellLeft(trip.getDriverName()));
            infoTable.addCell(labelCell("Helper"));
            infoTable.addCell(valueCellLeft(trip.getHelperName()));
            infoTable.addCell(labelCell("Total Wt Dispatched"));
            infoTable.addCell(valueCellLeft(trip.getTotalWeightDispatched() != null ? trip.getTotalWeightDispatched() + " kg" : "-"));
            infoTable.addCell(labelCell("Distance / Mileage"));
            infoTable.addCell(valueCellLeft(
                    (trip.getDistanceKm() != null ? trip.getDistanceKm() + " km" : "-") + " / " +
                    (trip.getMileage() != null ? trip.getMileage() : "-")));
            doc.add(infoTable);
            doc.add(Chunk.NEWLINE);

            var purchases = purchasesByTrip.getOrDefault(trip.getId(), List.of());
            if (!purchases.isEmpty()) {
                doc.add(new Paragraph("Picked up from suppliers:", SUB_FONT));
                PdfPTable pTable = new PdfPTable(new float[]{2, 1.3f, 1});
                pTable.setWidthPercentage(100);
                addHeaderRow(pTable, "Supplier", "Weight", "Birds");
                for (PurchaseResponse p : purchases) {
                    pTable.addCell(cell(p.getSupplierName()));
                    pTable.addCell(cell(p.getPurchaseWeight() + " kg"));
                    pTable.addCell(cell(p.getNumberOfBirds() != null ? String.valueOf(p.getNumberOfBirds()) : "-"));
                }
                doc.add(pTable);
                doc.add(Chunk.NEWLINE);
            }

            var deliveries = deliveriesByTrip.getOrDefault(trip.getId(), List.of());
            if (!deliveries.isEmpty()) {
                doc.add(new Paragraph("Delivered to chicken centers:", SUB_FONT));
                PdfPTable dTable = new PdfPTable(new float[]{2, 1, 1.3f, 1.3f});
                dTable.setWidthPercentage(100);
                addHeaderRow(dTable, "Customer", "Birds", "Weight", "Amount");
                java.math.BigDecimal deliveredTotal = java.math.BigDecimal.ZERO;
                for (DeliveryResponse d : deliveries) {
                    dTable.addCell(cell(d.getCustomerName()));
                    dTable.addCell(cell(d.getNumberOfBirds() != null ? String.valueOf(d.getNumberOfBirds()) : "-"));
                    dTable.addCell(cell(d.getDispatchWeight() + " kg"));
                    dTable.addCell(cell(rupees(d.getSalesAmount())));
                    deliveredTotal = deliveredTotal.add(d.getDispatchWeight() == null ? java.math.BigDecimal.ZERO : d.getDispatchWeight());
                }
                doc.add(dTable);
                doc.add(Chunk.NEWLINE);

                if (trip.getTotalWeightDispatched() != null) {
                    var diff = trip.getTotalWeightDispatched().subtract(deliveredTotal);
                    doc.add(totalsBlock(
                            "Total Weight Dispatched", trip.getTotalWeightDispatched() + " kg",
                            "Total Delivered (sum of stops)", deliveredTotal + " kg",
                            "Difference", diff + " kg"));
                }
            }
            doc.add(Chunk.NEWLINE);
        }

        doc.close();
        return out.toByteArray();
    }

    private PdfPCell valueCellLeft(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "-" : text, NORMAL_FONT));
        c.setPadding(6f);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    // ---------------------------------------------------------------
    // Expense PDF
    // ---------------------------------------------------------------
    public byte[] expenseReport(LocalDate from, LocalDate to, List<ExpenseResponse> expenses) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Expense Report"));
        doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), NORMAL_FONT));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1.3f, 1.5f, 3, 1.3f});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Date", "Category", "Description", "Amount");
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (ExpenseResponse e : expenses) {
            table.addCell(cell(e.getExpenseDate().format(DATE_FMT)));
            table.addCell(cell(e.getCategory()));
            table.addCell(cell(e.getDescription() != null ? e.getDescription() : "-"));
            table.addCell(cell(rupees(e.getAmount())));
            total = total.add(e.getAmount() == null ? java.math.BigDecimal.ZERO : e.getAmount());
        }
        doc.add(table);
        doc.add(Chunk.NEWLINE);
        doc.add(totalsBlock("Total", rupees(total)));

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------
    // Profit / Loss PDF
    // ---------------------------------------------------------------
    public byte[] profitLoss(ProfitLossResponse pl) throws Exception {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(footer());
        doc.open();

        addLetterhead(doc);
        doc.add(sectionTitle("Estimated Profit / Loss Report"));
        doc.add(new Paragraph("Period: " + pl.getFromDate().format(DATE_FMT) + " to " + pl.getToDate().format(DATE_FMT), NORMAL_FONT));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{2, 1.5f});
        table.setWidthPercentage(60);
        table.addCell(labelCell("Sales Revenue"));
        table.addCell(valueCell(rupees(pl.getSalesRevenue())));
        table.addCell(labelCell("Purchase Cost"));
        table.addCell(valueCell("- " + rupees(pl.getPurchaseCost())));
        table.addCell(labelCell("Recorded Expenses"));
        table.addCell(valueCell("- " + rupees(pl.getRecordedExpenses())));
        table.addCell(labelCell("Estimated Profit/Loss"));
        table.addCell(valueCell(rupees(pl.getEstimatedProfitLoss())));
        doc.add(table);

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------

    private Document newDocument() {
        return new Document(PageSize.A4, 36, 36, 90, 50);
    }

    private void addLetterhead(Document doc) throws DocumentException {
        var settings = farmSettingsService.get();
        Paragraph title = new Paragraph(settings.getFarmName(), TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        if ((settings.getOwnerName() != null && !settings.getOwnerName().isBlank())
                || (settings.getOwnerPhone() != null && !settings.getOwnerPhone().isBlank())) {
            String contact = String.join(" \u00b7 ",
                    java.util.stream.Stream.of(settings.getOwnerName(), settings.getOwnerPhone())
                            .filter(s -> s != null && !s.isBlank())
                            .toList());
            Paragraph ownerLine = new Paragraph(contact, SMALL_FONT);
            ownerLine.setAlignment(Element.ALIGN_CENTER);
            doc.add(ownerLine);
        }

        Paragraph generated = new Paragraph("Generated: " +
                LocalDate.now().format(DATE_FMT), SMALL_FONT);
        generated.setAlignment(Element.ALIGN_CENTER);
        doc.add(generated);
        doc.add(Chunk.NEWLINE);
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, SUB_FONT);
        p.setSpacingAfter(6f);
        return p;
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(5f);
            table.addCell(cell);
        }
    }

    private PdfPCell cell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, NORMAL_FONT));
        cell.setPadding(4f);
        return cell;
    }

    private PdfPCell labelCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, SUB_FONT));
        c.setPadding(6f);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private PdfPCell valueCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, NORMAL_FONT));
        c.setPadding(6f);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPTable totalsBlock(String... labelValuePairs) {
        PdfPTable table = new PdfPTable(new float[]{2, 1.5f});
        table.setWidthPercentage(55);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            table.addCell(labelCell(labelValuePairs[i]));
            table.addCell(valueCell(labelValuePairs[i + 1]));
        }
        return table;
    }

    private String rupees(java.math.BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private PdfPageEventHelper footer() {
        return new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                PdfPTable table = new PdfPTable(1);
                try {
                    table.setTotalWidth(document.getPageSize().getWidth() - 72);
                } catch (Exception ignored) {
                }
                PdfPCell cell = new PdfPCell(new Phrase("Page " + writer.getPageNumber(), SMALL_FONT));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
                table.writeSelectedRows(0, -1, 36, 36, writer.getDirectContent());
            }
        };
    }
}
