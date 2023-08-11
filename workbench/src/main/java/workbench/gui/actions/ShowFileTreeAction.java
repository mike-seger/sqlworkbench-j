package workbench.gui.actions;

import java.awt.event.ActionEvent;

import workbench.gui.MainWindow;

public class ShowFileTreeAction
  extends WbAction
{
  private MainWindow mainWin;

  public ShowFileTreeAction(MainWindow mainWin)
  {
    super();
    this.mainWin = mainWin;
    initMenuDefinition("MnuTxtNewFileTreeWindow");
    setEnabled(true);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    mainWin.showFileTree(true);
  }
}
