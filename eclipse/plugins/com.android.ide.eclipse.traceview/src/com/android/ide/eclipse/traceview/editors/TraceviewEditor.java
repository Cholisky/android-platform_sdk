/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.traceview.editors;

import com.android.traceview.ColorController;
import com.android.traceview.DmTraceReader;
import com.android.traceview.MethodData;
import com.android.traceview.ProfileView;
import com.android.traceview.SelectionController;
import com.android.traceview.TimeLineView;
import com.android.traceview.TraceReader;
import com.android.traceview.TraceUnits;
import com.android.traceview.ProfileView.MethodHandler;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;

public class TraceviewEditor extends EditorPart implements MethodHandler {

    private Composite mParent;
    private String mFilename;
    private Composite mContents;

    @Override
    public void doSave(IProgressMonitor monitor) {
        // We do not modify the file
    }

    @Override
    public void doSaveAs() {
        // We do not modify the file
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        // The contract of init() mentions we need to fail if we can't
        // understand the input.
        if (input instanceof FileEditorInput) {
            // We try to open a file that is part of the current workspace
            FileEditorInput fileEditorInput = (FileEditorInput) input;
            mFilename = fileEditorInput.getPath().toOSString();
            setSite(site);
            setInput(input);
            setPartName(input.getName());
        } else if (input instanceof FileStoreEditorInput) {
            // We try to open a file that is not part of the current workspace
            FileStoreEditorInput fileStoreEditorInput = (FileStoreEditorInput) input;
            mFilename = fileStoreEditorInput.getURI().getPath();
            setSite(site);
            setInput(input);
            setPartName(input.getName());
        } else {
            throw new PartInitException("Input is not of type FileEditorInput " + //$NON-NLS-1$
                    "nor FileStoreEditorInput: " + //$NON-NLS-1$
                    input == null ? "null" : input.toString()); //$NON-NLS-1$
        }
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void createPartControl(Composite parent) {
        mParent = parent;
        TraceReader reader = new DmTraceReader(mFilename, false);
        reader.getTraceUnits().setTimeScale(TraceUnits.TimeScale.MilliSeconds);

        mContents = new Composite(mParent, SWT.NONE);

        Display display = mContents.getDisplay();
        ColorController.assignMethodColors(display, reader.getMethods());
        SelectionController selectionController = new SelectionController();

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;
        mContents.setLayout(gridLayout);

        Color darkGray = display.getSystemColor(SWT.COLOR_DARK_GRAY);

        // Create a sash form to separate the timeline view (on top)
        // and the profile view (on bottom)
        SashForm sashForm1 = new SashForm(mContents, SWT.VERTICAL);
        sashForm1.setBackground(darkGray);
        sashForm1.SASH_WIDTH = 3;
        GridData data = new GridData(GridData.FILL_BOTH);
        sashForm1.setLayoutData(data);

        // Create the timeline view
        new TimeLineView(sashForm1, reader, selectionController);

        // Create the profile view
        new ProfileView(sashForm1, reader, selectionController).setMethodHandler(this);

        mParent.layout();
    }

    @Override
    public void setFocus() {
        mParent.setFocus();
    }

    private static class TraceSearchRequestor extends SearchRequestor {
        private MethodData mMethod;

        public TraceSearchRequestor(MethodData method) {
            mMethod = method;
        }

        @Override
        public void acceptSearchMatch(SearchMatch match) throws CoreException {
            Object element = match.getElement();
            if (element instanceof IMethod) {
                IMethod method = (IMethod) element;
                if (method.getSignature().equals(mMethod.getSignature())) {
                    JavaUI.openInEditor(method);
                }
            }
        }
    }

    // ---- MethodHandler methods

    public void handleMethod(MethodData method) {
        // it's a bit complicated to convert the signature we have with what the
        // search engine require, so we'll search by name only and test the signature
        // when we get the result(s).
        String methodName = method.getMethodName();
        String className = method.getClassName().replaceAll("/", ".");  //$NON-NLS-1$  //$NON-NLS-21$
        String fqmn = className + "." + methodName; //$NON-NLS-1$

        try {
            SearchEngine se = new SearchEngine();
            se.search(SearchPattern.createPattern(
                    fqmn,
                    IJavaSearchConstants.METHOD,
                    IJavaSearchConstants.DECLARATIONS,
                    SearchPattern.R_EXACT_MATCH
                    | SearchPattern.R_CASE_SENSITIVE),
                    new SearchParticipant[] { SearchEngine .getDefaultSearchParticipant() },
                SearchEngine.createWorkspaceScope(),
                new TraceSearchRequestor(method),
                new NullProgressMonitor());
        } catch (CoreException e) {

        }
    }

    // -----

}
