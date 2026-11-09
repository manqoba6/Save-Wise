package com.savewise.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.savewise.dto.BudgetResponse;
import com.savewise.dto.ReportResponse;
import com.savewise.dto.ReportResponse.*;
import com.savewise.exception.ApiException;
import com.savewise.util.CurrencyFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportExportService {
    /** Longer lists are cut in the PDF (the CSV always has every row). */
    private static final int PDF_MAX_TRANSACTIONS = 500;

    private static final Color HEADER_BG = new Color(0x1F, 0x4E, 0x79);
    private static final Color STRIPE_BG = new Color(0xF2, 0xF6, 0xFA);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, new Color(0x1F, 0x4E, 0x79));
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.NORMAL, Color.BLACK);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.BLACK);
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    private final Clock clock;

    public ReportExportService(Clock clock) { this.clock = clock; }

    // ======================= CSV =======================

    /** One row per transaction, UTF-8 with a BOM so Excel shows accented characters correctly. */
    public byte[] csv(ReportResponse r) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Date,Type,Category,Description,Amount,Currency\r\n");
        for (TransactionLine t : r.transactions()) {
            sb.append(t.date()).append(',')
              .append(t.type()).append(',')
              .append(csvText(t.categoryName())).append(',')
              .append(csvText(t.description())).append(',')
              .append(t.amount().toPlainString()).append(',')
              .append(r.currency()).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Quotes fields that need it, and defuses spreadsheet formulas: a description such as
     * "=HYPERLINK(...)" would otherwise run when the file is opened in Excel.
     */
    static String csvText(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ======================= PDF =======================

    public byte[] pdf(ReportResponse r, String preparedFor) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageNumbers());
            doc.open();

            doc.add(new Paragraph("SaveWise", TITLE));
            doc.add(new Paragraph(capitalise(r.type()) + " report: " + r.label(), H2));
            doc.add(new Paragraph("Prepared for " + preparedFor + " on " + LocalDate.now(clock)
                    + "   |   All amounts in " + r.currency(), MUTED));
            doc.add(space(8));

            summary(doc, r);
            categories(doc, "Income by category", r.incomeByCategory(), r.currency());
            categories(doc, "Expenses by category", r.expensesByCategory(), r.currency());
            if (!r.budgets().isEmpty()) budgets(doc, r.budgets(), r.currency());
            if (!r.breakdown().isEmpty()) breakdown(doc, r);
            transactions(doc, r);

            doc.close();
        } catch (DocumentException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate the PDF.");
        }
        return out.toByteArray();
    }

    private void summary(Document doc, ReportResponse r) throws DocumentException {
        Summary s = r.summary();
        String cur = r.currency();
        PdfPTable t = table(new float[]{60, 40});
        row(t, "Total income", money(s.totalIncome(), cur), false);
        row(t, "Total expenses", money(s.totalExpenses(), cur), true);
        row(t, "Net", money(s.net(), cur), false);
        row(t, "Savings rate", s.savingsRatePercent() == null ? "n/a (no income)" : s.savingsRatePercent() + "%", true);
        row(t, "Savings deposits", money(s.savingsDeposits(), cur), false);
        row(t, "Savings withdrawals", money(s.savingsWithdrawals(), cur), true);
        row(t, "Transactions", String.valueOf(s.transactionCount()), false);
        doc.add(heading("Summary"));
        doc.add(t);
    }

    private void categories(Document doc, String title, List<CategoryLine> lines, String cur) throws DocumentException {
        doc.add(heading(title));
        if (lines.isEmpty()) { doc.add(new Paragraph("No records in this period.", MUTED)); return; }
        PdfPTable t = table(new float[]{50, 30, 20});
        head(t, "Category", "Amount", "Share");
        boolean stripe = false;
        for (CategoryLine l : lines) {
            cell(t, l.categoryName(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, money(l.amount(), cur), BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, l.percentOfTotal() + "%", BODY, Element.ALIGN_RIGHT, stripe);
            stripe = !stripe;
        }
        doc.add(t);
    }

    private void budgets(Document doc, List<BudgetResponse> budgets, String cur) throws DocumentException {
        doc.add(heading("Budgets for the month"));
        PdfPTable t = table(new float[]{28, 20, 20, 16, 16});
        head(t, "Category", "Budget", "Spent", "Used", "Status");
        boolean stripe = false;
        for (BudgetResponse b : budgets) {
            cell(t, b.categoryName(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, money(b.budgetAmount(), cur), BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, money(b.spent(), cur), BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, b.percentUsed() + "%", BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, b.status(), "OK".equals(b.status()) ? BODY : BOLD, Element.ALIGN_LEFT, stripe);
            stripe = !stripe;
        }
        doc.add(t);
    }

    private void breakdown(Document doc, ReportResponse r) throws DocumentException {
        doc.add(heading("ANNUAL".equals(r.type()) ? "Month by month" : "Day by day"));
        PdfPTable t = table(new float[]{25, 25, 25, 25});
        head(t, "Period", "Income", "Expenses", "Net");
        boolean stripe = false;
        for (BreakdownLine b : r.breakdown()) {
            cell(t, b.label(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, money(b.income(), r.currency()), BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, money(b.expenses(), r.currency()), BODY, Element.ALIGN_RIGHT, stripe);
            cell(t, money(b.net(), r.currency()), BODY, Element.ALIGN_RIGHT, stripe);
            stripe = !stripe;
        }
        doc.add(t);
    }

    private void transactions(Document doc, ReportResponse r) throws DocumentException {
        doc.add(heading("Transactions"));
        List<TransactionLine> all = r.transactions();
        if (all.isEmpty()) { doc.add(new Paragraph("No transactions in this period.", MUTED)); return; }
        PdfPTable t = table(new float[]{14, 11, 20, 33, 22});
        head(t, "Date", "Type", "Category", "Description", "Amount");
        boolean stripe = false;
        for (TransactionLine l : all.subList(0, Math.min(all.size(), PDF_MAX_TRANSACTIONS))) {
            cell(t, l.date().toString(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, l.type(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, l.categoryName(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, l.description() == null ? "" : l.description(), BODY, Element.ALIGN_LEFT, stripe);
            cell(t, money(l.amount(), r.currency()), BODY, Element.ALIGN_RIGHT, stripe);
            stripe = !stripe;
        }
        t.setHeaderRows(1);
        doc.add(t);
        if (all.size() > PDF_MAX_TRANSACTIONS) {
            doc.add(new Paragraph("Showing the first " + PDF_MAX_TRANSACTIONS + " of " + all.size()
                    + " transactions. Export the CSV for the complete list.", MUTED));
        }
    }

    // ---------- small layout helpers ----------

    private static String money(BigDecimal v, String cur) {
        return CurrencyFormatter.formatWithCode(v, cur);
    }

    private static String capitalise(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static Paragraph heading(String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(14);
        p.setSpacingAfter(4);
        return p;
    }

    private static Paragraph space(float h) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(h);
        return p;
    }

    private static PdfPTable table(float[] widths) throws DocumentException {
        PdfPTable t = new PdfPTable(widths.length);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        return t;
    }

    private static void head(PdfPTable t, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(labels[i], HEAD));
            c.setBackgroundColor(HEADER_BG);
            c.setPadding(5);
            c.setHorizontalAlignment(i == 0 || labels[i].equals("Type") || labels[i].equals("Description")
                    || labels[i].equals("Category") || labels[i].equals("Status") || labels[i].equals("Date")
                    || labels[i].equals("Period") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        t.setHeaderRows(1);
    }

    private static void row(PdfPTable t, String label, String value, boolean stripe) {
        cell(t, label, BOLD, Element.ALIGN_LEFT, stripe);
        cell(t, value, BODY, Element.ALIGN_RIGHT, stripe);
    }

    private static void cell(PdfPTable t, String text, Font font, int align, boolean stripe) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(new Color(0xD9, 0xD9, 0xD9));
        if (stripe) c.setBackgroundColor(STRIPE_BG);
        t.addCell(c);
    }

    /** Draws "Page N" at the bottom of every page. */
    private static class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("Page " + writer.getPageNumber(), MUTED),
                    (document.right() + document.left()) / 2, document.bottom() - 22, 0);
        }
    }
}
