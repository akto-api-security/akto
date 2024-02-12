package com.akto.notifications.content;

import com.akto.dto.notifications.content.*;

public class HTMLVisitor extends Visitor<Integer> {

    StringBuilder sb = new StringBuilder();

    private HTMLVisitor() {}

    @Override
    public Integer visit(CompositeContent compositeContent) {
        sb.append("<body>");
        for (Content content: compositeContent.getContents()) {
            super.visit(content);
        }
        sb.append("</body>");

        return 0;
    }

    @Override
    public Integer visit(ImageContent content) {
        sb.append("<img src=\"").append(content.getUrl()).append("\" width=").append(content.getWidth()).append("px alt=\"")
            .append(content.getName())
        .append("\"/>");

        return 0;
    }

    @Override
    public Integer visit(LinkedContent linkedContent) {
        sb.append("<a href=\"").append(linkedContent.getLink()).append("\">");
        super.visit(linkedContent.getContent());
        sb.append("</a>");

        return 0;
    }

    @Override
    public Integer visit(NewlineContent content) {
        sb.append("</br>");
        return 0;
    }

    @Override
    public Integer visit(StringContent content) {
        sb.append("<span style=\"color:").append(content.getFontColor()).append(";font-size:").append(content.getFontSize()).append("px;\">")
            .append(content.getStr())
        .append("</span>");

        return 0;
    }

    @Override
    public Integer visit(BlockContent blockContent) {
        sb.append("<div ");

        int[] margins = blockContent.getMargins();

        if (margins != null) {
            sb.append("style=");
                sb.append("\"margin:" + margins[0] +"px " + margins[1] +"px " + margins[2] +"px " + margins[3] +"px\"");
            sb.append(";");
        }

        sb.append(">");
        boolean isFirst = true;
        for (Content content: blockContent.getContents()) {
            sb.append("<span>").append(isFirst ? "": " ");
            super.visit(content);
            sb.append("</span>");
            isFirst = false;
        }
        sb.append("</div>");

        return 0;
    }

    public static String run(Content c) {
        HTMLVisitor visitor = new HTMLVisitor();
        visitor.visit(c);
        return visitor.sb.toString();
    }

}
