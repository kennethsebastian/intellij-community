package com.intellij.ide.commander;

import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

final class CommanderSelectInTarget extends SelectInTargetPsiWrapper {
  public CommanderSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return "Commander";
  }

  protected boolean canSelect(final PsiFile file) {
    return file.getManager().isInProject(file);
  }

  protected void select(PsiElement element, boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile) {
        break;
      }
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) {
        break;
      }
      element = element.getParent();
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement _element = element.getOriginalElement();

    selectElementInCommander(new Runnable() {
      public void run() {
        Commander.getInstance(myProject).selectElementInLeftPanel(_element, BasePsiNode.getVirtualFile(_element));
      }
    }, requestFocus);
  }

  private void selectElementInCommander(final Runnable runnable, final boolean requestFocus) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.COMMANDER).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectElementInCommander(new Runnable() {
      public void run() {
        final Commander commander = Commander.getInstance(myProject);
        commander.selectElementInLeftPanel(selector, virtualFile);
      }
    }, requestFocus);

  }

  protected boolean canWorkWithCustomObjects() {
    return true;
  }

  public String getToolWindowId() {
    return ToolWindowId.COMMANDER;
  }

  public String getMinorViewId() {
    return null;
  }
}
