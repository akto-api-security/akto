package com.akto.notifications.content;
import com.akto.dto.notifications.content.*;
import org.junit.Test;

import java.util.ArrayList;

public class HTMLVisitorTest {

    public BlockContent createBlock(String identifier) {
        ArrayList<Content> heading = new ArrayList<>();
        heading.add(new StringContent(identifier, 18, "#47466A"));

        ArrayList<Content> body = new ArrayList<>();
        body.add(
            new BlockContent(
                null,
                new LinkedContent(new StringContent(identifier, 14, "#47466A"), "https://google.com"),
                new StringContent("was down by ", 14, "#47466A"),
                new StringContent("13%", 14, "#F44336")
            )
        );

        String imgURL = "https://uploads.sitepoint.com/wp-content/uploads/2016/12/1480920152bootstrap.jpg";
        body.add(new LinkedContent(new ImageContent("img1", imgURL, 40), "https://www.google.com"));

        ArrayList<Content> contents = new ArrayList<>();
        contents.add(new BlockContent(null, heading));
        contents.add(new BlockContent(null, body));

        return new BlockContent(new int[]{20, 20, 20, 20}, contents);
    }

    public Content createTree() {

        ArrayList<BlockContent> blocks = new ArrayList<>();
        blocks.add(createBlock("Revenue"));
        blocks.add(createBlock("Traffic"));
        blocks.add(createBlock("Conv rate"));

        return new CompositeContent(blocks);
    }

    @Test
    public void printHTML() {
        System.out.println(HTMLVisitor.run(createTree()));
    }

    @Test
    public void printPDF() throws Exception {
        System.out.println(PDFVisitor.run(createTree(), "/var/tmp/test_ankush_visitor.pdf"));
    }

}