/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.12.2006
 * Time: 19:39:22
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FilterComponent;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CommittedChangesPanel extends JPanel implements TypeSafeDataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.CommittedChangesPanel");

  private final CommittedChangesTreeBrowser myBrowser;
  private final Project myProject;
  private CommittedChangesProvider myProvider;
  private ChangeBrowserSettings mySettings;
  private final RepositoryLocation myLocation;
  private int myMaxCount = 0;
  private final MyFilterComponent myFilterComponent = new MyFilterComponent();
  private final List<Runnable> myShouldBeCalledOnDispose;
  private volatile boolean myDisposed;
  private volatile boolean myInLoad;

  public CommittedChangesPanel(Project project, final CommittedChangesProvider provider, final ChangeBrowserSettings settings,
                               @Nullable final RepositoryLocation location, @Nullable ActionGroup extraActions) {
    super(new BorderLayout());
    mySettings = settings;
    myProject = project;
    myProvider = provider;
    myLocation = location;
    myShouldBeCalledOnDispose = new ArrayList<Runnable>();
    myBrowser = new CommittedChangesTreeBrowser(project, new ArrayList<CommittedChangeList>());
    Disposer.register(this, myBrowser);
    add(myBrowser, BorderLayout.CENTER);

    final VcsCommittedViewAuxiliary auxiliary = provider.createActions(myBrowser, location);

    JPanel toolbarPanel = new JPanel();
    toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.X_AXIS));

    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("CommittedChangesToolbar");

    ActionToolbar toolBar = myBrowser.createGroupFilterToolbar(project, group, extraActions,
                                                               auxiliary != null ? auxiliary.getToolbarActions() : Collections.<AnAction>emptyList());
    toolbarPanel.add(toolBar.getComponent());
    toolbarPanel.add(Box.createHorizontalGlue());
    toolbarPanel.add(myFilterComponent);
    myFilterComponent.setMinimumSize(myFilterComponent.getPreferredSize());
    myFilterComponent.setMaximumSize(myFilterComponent.getPreferredSize());
    myBrowser.setToolBar(toolbarPanel);

    if (auxiliary != null) {
      myShouldBeCalledOnDispose.add(auxiliary.getCalledOnViewDispose());
      myBrowser.setTableContextMenu(group, (auxiliary.getPopupActions() == null) ? Collections.<AnAction>emptyList() : auxiliary.getPopupActions());
    } else {
      myBrowser.setTableContextMenu(group, Collections.<AnAction>emptyList());
    }
    
    final AnAction anAction = ActionManager.getInstance().getAction("CommittedChanges.Refresh");
    anAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);
    myBrowser.addFilter(myFilterComponent);
  }

  public RepositoryLocation getRepositoryLocation() {
    return myLocation;
  }

  public void setMaxCount(final int maxCount) {
    myMaxCount = maxCount;
  }

  public void setProvider(final CommittedChangesProvider provider) {
    if (myProvider != provider) {
      myProvider = provider;
      mySettings = provider.createDefaultSettings(); 
    }
  }

  public void refreshChanges(final boolean cacheOnly) {
    if (myLocation != null) {
      refreshChangesFromLocation();
    }
    else {
      refreshChangesFromCache(cacheOnly);
    }
  }

  private void refreshChangesFromLocation() {
    myBrowser.reset();

    myInLoad = true;
    myBrowser.setLoading(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Loading changes", true, BackgroundFromStartOption.getInstance()) {
      
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final AsynchConsumer<List<CommittedChangeList>> appender = new AsynchConsumer<List<CommittedChangeList>>() {
            public void finished() {
            }

            public void consume(final List<CommittedChangeList> list) {
              new AbstractCalledLater(myProject, ModalityState.stateForComponent(myBrowser)) {
                public void run() {
                  myBrowser.append(list);
                }
              }.callMe();
            }
          };
          final BufferedListConsumer<CommittedChangeList> bufferedListConsumer = new BufferedListConsumer<CommittedChangeList>(30, appender,-1);

          myProvider.loadCommittedChanges(mySettings, myLocation, myMaxCount, new AsynchConsumer<CommittedChangeList>() {
            public void finished() {
              bufferedListConsumer.flush();
            }
            public void consume(CommittedChangeList committedChangeList) {
              if (myDisposed) {
                indicator.cancel();
              }
              ProgressManager.checkCanceled();
              bufferedListConsumer.consumeOne(committedChangeList);
            }
          });
        }
        catch (final VcsException e) {
          LOG.info(e);
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
            public void run() {
              Messages.showErrorDialog(myProject, "Error refreshing view: " + StringUtil.join(e.getMessages(), "\n"), "Committed Changes");
            }
          }, null, myProject);
        } finally {
          myInLoad = false;
          myBrowser.setLoading(false);
        }
      }
    });
  }

  public void clearCaches() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.clearCaches(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            updateFilteredModel(Collections.<CommittedChangeList>emptyList());
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  private void refreshChangesFromCache(final boolean cacheOnly) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.hasCachesForAnyRoot(new Consumer<Boolean>() {
      public void consume(final Boolean notEmpty) {
        if (! notEmpty) {
          if (cacheOnly) {
            myBrowser.getEmptyText().setText(VcsBundle.message("committed.changes.not.loaded.message"));
            return;
          }
          if (!CacheSettingsDialog.showSettingsDialog(myProject)) return;
        }
        cache.getProjectChangesAsync(mySettings, myMaxCount, cacheOnly,
                                     new Consumer<List<CommittedChangeList>>() {
                                       public void consume(final List<CommittedChangeList> committedChangeLists) {
                                         updateFilteredModel(committedChangeLists);
                                         }
                                       },
                                     new Consumer<List<VcsException>>() {
                                       public void consume(final List<VcsException> vcsExceptions) {
                                         AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, "Error refreshing VCS history");
                                       }
                                     });
      }
    });
  }

  private static class FilterHelper {
    private final String[] myParts;

    FilterHelper(final String filterString) {
      myParts = filterString.split(" ");
      for(int i = 0; i < myParts.length; ++ i) {
        myParts [i] = myParts [i].toLowerCase();
      }
    }

    public boolean filter(@NotNull final CommittedChangeList cl) {
      return changeListMatches(cl, myParts);
    }

    private static boolean changeListMatches(@NotNull final CommittedChangeList changeList, final String[] filterWords) {
      for(String word: filterWords) {
        final String comment = changeList.getComment();
        final String committer = changeList.getCommitterName();
        if ((comment != null && comment.toLowerCase().indexOf(word) >= 0) ||
            (committer != null && committer.toLowerCase().indexOf(word) >= 0) ||
            Long.toString(changeList.getNumber()).indexOf(word) >= 0) {
          return true;
        }
      }
      return false;
    }
  }

  private void updateFilteredModel(List<CommittedChangeList> committedChangeLists) {
    if (committedChangeLists == null) {
      return;
    }
    myBrowser.getEmptyText().setText(VcsBundle.message("committed.changes.empty.message"));
    myBrowser.setItems(committedChangeLists, CommittedChangesBrowserUseCase.COMMITTED);
  }

  public void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI(true), mySettings);
    filterDialog.show();
    if (filterDialog.isOK()) {
      mySettings = filterDialog.getSettings();
      refreshChanges(false);
    }
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(VcsDataKeys.CHANGES) || key.equals(VcsDataKeys.CHANGE_LISTS)) {
      myBrowser.calcData(key, sink);
    }
  }

  public void dispose() {
    for (Runnable runnable : myShouldBeCalledOnDispose) {
      runnable.run();
    }
    myDisposed = true;
  }

  private class MyFilterComponent extends FilterComponent implements ChangeListFilteringStrategy {
    private final List<ChangeListener> myList;

    public MyFilterComponent() {
      super("COMMITTED_CHANGES_FILTER_HISTORY", 20);
      myList = new ArrayList<ChangeListener>();
    }

    @Override
    public CommittedChangesFilterKey getKey() {
      return new CommittedChangesFilterKey("text", CommittedChangesFilterPriority.TEXT);
    }

    public void filter() {
      for (ChangeListener changeListener : myList) {
        changeListener.stateChanged(new ChangeEvent(this));
      }
    }
    public JComponent getFilterUI() {
      return null;
    }
    public void setFilterBase(List<CommittedChangeList> changeLists) {
    }
    public void addChangeListener(ChangeListener listener) {
      myList.add(listener);
    }
    public void removeChangeListener(ChangeListener listener) {
      myList.remove(listener);
    }
    public void resetFilterBase() {
    }
    public void appendFilterBase(List<CommittedChangeList> changeLists) {
    }
    @NotNull
    public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
      final FilterHelper filterHelper = new FilterHelper(myFilterComponent.getFilter());
      final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
      for (CommittedChangeList list : changeLists) {
        if (filterHelper.filter(list)) {
          result.add(list);
        }
      }
      return result;
    }
  }

  public void passCachedListsToListener(final VcsConfigurationChangeListener.DetailedNotification notification,
                                        final Project project, final VirtualFile root) {
    final LinkedList<CommittedChangeList> resultList = new LinkedList<CommittedChangeList>();
    myBrowser.reportLoadedLists(new CommittedChangeListsListener() {
      public void onBeforeStartReport() {
      }
      public boolean report(CommittedChangeList list) {
        resultList.add(list);
        return false;
      }
      public void onAfterEndReport() {
        if (! resultList.isEmpty()) {
          notification.execute(project, root, resultList);
        }
      }
    });
  }

  public boolean isInLoad() {
    return myInLoad;
  }
}
