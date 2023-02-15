package com.akto.notifications.content;

import com.akto.dto.notifications.content.*;
import com.akto.notifications.util.ImageFetcher;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class PDFVisitor extends Visitor<Element> {

    Document document;
    static String uuid() {
        return UUID.randomUUID().toString();
    };
    String outputFilename;

    private PDFVisitor(String filename) throws Exception {
        this.outputFilename = filename;
    }

    @Override
    public Element visit(CompositeContent compositeContent) {
        document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(outputFilename));
            document.open();

            for (Content content: compositeContent.getContents()) {
                document.add(super.visit(content));
                document.add(Chunk.NEWLINE);
            }

            document.close();
        } catch (DocumentException e) {

        } catch (FileNotFoundException e) {

        }

        return null;
    }

    @Override
    public Element visit(ImageContent content) {
        try {
            String imgPath = "/var/tmp/"+uuid();
            ImageFetcher.saveImage(content.getUrl(), imgPath);
            Image img = Image.getInstance(imgPath);
            img.scalePercent(20000.0f/img.getPlainHeight());
            return img;
        } catch (BadElementException e) {

        } catch (IOException e) {

        }

        return null;
    }

    @Override
    public Element visit(LinkedContent linkedContent) {

        Content inner = linkedContent.getContent();
        switch (linkedContent.getContent().type()) {
            case IMAGE:
                ImageContent imageContent = (ImageContent) inner;
                Image image = (Image) visit(imageContent);
                image.setAnnotation(new Annotation(0,0,0,0, linkedContent.getLink()));
                return image;
            case STRING:
                StringContent stringContent = (StringContent) inner;
                Anchor anchor = new Anchor(visit(stringContent));
                anchor.setReference(linkedContent.getLink());
                return anchor;
        }

        return null;
    }

    @Override
    public Element visit(NewlineContent content) {
        return Chunk.NEWLINE;
    }

    @Override
    public Chunk visit(StringContent content) {
        return new Chunk(content.getStr());
    }

    @Override
    public Element visit(BlockContent blockContent) {
        Paragraph paragraph = new Paragraph();
        for(Content content: blockContent.getContents()) {
            paragraph.add(super.visit(content));
            paragraph.add(" ");
        }

        return paragraph;
    }

    public static String run(Content content, String filename) throws Exception {
        PDFVisitor visitor = new PDFVisitor(filename);
        visitor.visit(content);
        return filename;
    }
}
