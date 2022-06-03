package io.jmix.flowui.action.screen;

import io.jmix.flowui.action.ActionType;
import com.vaadin.flow.component.icon.VaadinIcon;
import io.jmix.core.Messages;
import io.jmix.flowui.kit.component.FlowUiComponentUtils;
import io.jmix.flowui.screen.StandardEditor;
import org.springframework.beans.factory.annotation.Autowired;

@ActionType(EditorDiscardAction.ID)
public class EditorDiscardAction<E> extends OperationResultScreenAction<EditorDiscardAction<E>, StandardEditor<E>> {

    public static final String ID = "editor_discard";

    public EditorDiscardAction() {
        this(ID);
    }

    public EditorDiscardAction(String id) {
        super(id);
    }

    @Override
    protected void initAction() {
        super.initAction();

        this.icon = FlowUiComponentUtils.iconToSting(VaadinIcon.BAN);
    }

    @Autowired
    protected void setMessages(Messages messages) {
        this.text = messages.getMessage("actions.Cancel");
    }

    @Override
    public void execute() {
        checkTarget();

        operationResult = target.closeWithDiscard();

        super.execute();
    }
}