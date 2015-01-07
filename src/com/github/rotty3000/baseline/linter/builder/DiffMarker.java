/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.github.rotty3000.baseline.linter.builder;

import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.service.diff.Delta;
import aQute.bnd.service.diff.Diff;
import aQute.bnd.service.diff.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * @author Raymond Augé
 */
public class DiffMarker {

	public DiffMarker(IJavaProject javaProject) {
		this.javaProject = javaProject;
	}

	public void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(
				LinterConstants.MARKER_TYPE, false, IResource.DEPTH_ZERO);
		}
		catch (CoreException ce) {
			ce.printStackTrace();
		}
	}

	public void deleteMarkers(IProject project) {
		try {
			project.deleteMarkers(
				LinterConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);
		}
		catch (CoreException ce) {
			ce.printStackTrace();
		}
	}

	public void doAccess(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		String delta = diff.getDelta().toString();

		StringBuffer sb = new StringBuffer();

		sb.append(delta.substring(0, 1));
		sb.append(delta.substring(1).toLowerCase());
		sb.append(" ");
		sb.append(diff.getName());
		sb.append("! Change package version to ");
		sb.append(info.suggestedVersion);

		if (diff.getType() == Type.CLASS) {
			doType2(file, parentDiff.getName(), info, sb.toString());
		}
		else {
			System.out.println(
				String.format(
					"%s %s %s", diff.getDelta(), diff.getType(),
					diff.getName()));
		}
	}

	public void doAnnotated(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		IType type = javaProject.findType(parentDiff.getName());
		ISourceRange range = type.getNameRange();

		for (IAnnotation annotation : type.getAnnotations()) {
			String diffName = diff.getName();
			String elementName = annotation.getElementName();

			int posDiff = diffName.lastIndexOf(".") + 1;
			int posElement = elementName.lastIndexOf(".") + 1;

			diffName = diffName.substring(posDiff);
			elementName = elementName.substring(posElement);

			if (elementName.equals(diffName)) {
				range = annotation.getNameRange();

				break;
			}
		}

		int lineNumber = getLineNumber(type, range.getOffset());
		IResource resource = type.getResource();
		String delta = diff.getDelta().toString().toLowerCase();

		addRangeMarker(
			(IFile)resource,
			"Member annotation " + delta + "! Change package version to " +
				info.suggestedVersion, range, lineNumber,
			IMarker.SEVERITY_ERROR);
	}

	public void doAnnotation(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType1(file, diff, info, "Annotation ");
	}

	public void doClass(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType1(file, diff, info, "Class ");
	}

//	public void doClassVersion(
//			IFile file, Diff diff, Diff parentDiff, Info info)
//		throws Exception {
//
//		if ((parentDiff.getDelta() == Delta.ADDED) ||
//			(diff.getDelta() != Delta.ADDED)) {
//
//			return;
//		}
//
//		System.out.println(
//			String.format(
//				"%s %s %s", diff.getDelta(), diff.getType(), diff.getName()));
//	}

	public void doConstant(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		System.out.println(
			String.format(
				"%s %s %s", diff.getDelta(), diff.getType(), diff.getName()));
	}

//	public void doDeprecated(IFile file, Diff diff, Diff parentDiff, Info info)
//		throws Exception {
//
//		if ((parentDiff.getDelta() == Delta.ADDED) ||
//			(diff.getDelta() != Delta.ADDED)) {
//
//			return;
//		}
//
//		System.out.println(
//			String.format(
//				"%s %s %s", diff.getDelta(), diff.getType(), diff.getName()));
//	}

	public void doEnum(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType1(file, diff, info, "Enum ");
	}

	public void doExtends(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType2(
			file, parentDiff.getName(), info,
			"Extends " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion);
	}

	public void doField(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		IType type = javaProject.findType(parentDiff.getName());
		ISourceRange range = type.getNameRange();

		for (IField field : type.getFields()) {
			String name = diff.getName().split("\\s", 2)[1].trim();

			if (field.getElementName().equals(name)) {
				range = field.getNameRange();

				break;
			}
		}

		int lineNumber = getLineNumber(type, range.getOffset());
		IResource resource = type.getResource();

		addRangeMarker(
			(IFile)resource,
			"Field " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion,
			range, lineNumber, IMarker.SEVERITY_ERROR);
	}

	public void doImplements(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		IType type = javaProject.findType(parentDiff.getName());

		if (type.getSuperInterfaceNames().length <= 0) {
			return;
		}

		doType2(
			file, parentDiff.getName(), info,
			"Implements " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion);
	}

	public void doInterface(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType1(file, diff, info, "Interface ");
	}

	public void doMethod(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		IType type = javaProject.findType(parentDiff.getName());

		String methodName = diff.getName();
		String[] parts = methodName.substring(
			0, methodName.length() - 1).split("\\(", 2);
		String[] args = parts[1].split(",");

		if ((args.length == 1) && (args[0].length() == 0)) {
			args = new String[0];
		}

		IMethod method = type.getMethod(parts[0], args);

		if (!method.exists()) {
			return;
		}

		ISourceRange nameRange = method.getNameRange();
		int lineNumber = getLineNumber(
			type, nameRange.getOffset());

		IResource resource = type.getResource();

		addRangeMarker(
			(IFile)resource,
			"Method " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion,
			nameRange, lineNumber, IMarker.SEVERITY_ERROR);
	}

	public void doPackage(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		String message = null;

		if (diff.getDelta() == Delta.ADDED) {
			message = "Missing package version (set > 0.0.0)";
		}

		if (message == null) {
			return;
		}

		IPackageFragment packageFragment = (IPackageFragment)JavaCore.create(
			file.getParent());

		for (ICompilationUnit compilationUnit :
				packageFragment.getCompilationUnits()) {

			if (compilationUnit.getElementName().equals("package-info.java")) {
				continue;
			}

			addLineMarker(
				(IFile)compilationUnit.getResource(), message, 1,
				IMarker.SEVERITY_ERROR);
		}
	}

	public void doParameter(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		IType type = javaProject.findType(parentDiff.getName());

		String methodName = diff.getName();
		String[] parts = methodName.substring(
			0, methodName.length() - 1).split("\\(", 2);
		String[] args = parts[1].split(",");

		if ((args.length == 1) && (args[0].length() == 0)) {
			args = new String[0];
		}

		IMethod method = type.getMethod(parts[0], args);

		if (!method.exists()) {
			return;
		}

		ISourceRange nameRange = method.getNameRange();
		int lineNumber = getLineNumber(
			type, nameRange.getOffset());

		IResource resource = type.getResource();

		addRangeMarker(
			(IFile)resource,
			"Parameter " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion,
			nameRange, lineNumber, IMarker.SEVERITY_ERROR);
	}

	public void doProperty(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		System.out.println(
			String.format(
				"%s %s %s", diff.getDelta(), diff.getType(), diff.getName()));
	}

	public void doReturn(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

		doType2(
			file, parentDiff.getName(), info,
			"Return " + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion);
	}

	public void doVersion(IFile file, Diff diff, Diff parentDiff, Info info)
		throws Exception {

		if ((parentDiff.getDelta() == Delta.ADDED) ||
			(diff.getDelta() != Delta.ADDED)) {

			return;
		}

//		System.out.println(
//			String.format(
//				"%s %s %s", diff.getDelta(), diff.getType(), diff.getName()));
	}

	protected void addLineMarker(
		IFile file, String message, int lineNumber, int severity) {

		try {
			IMarker marker = file.createMarker(LinterConstants.MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);

			if (lineNumber == -1) {
				lineNumber = 1;
			}

			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		}
		catch (CoreException e) {
			//ignore
		}
	}

	protected void addRangeMarker(
		final IFile file, String message, ISourceRange sourceRange,
		int lineNumber, int severity) {

		try {
			IMarker[] markers = file.findMarkers(
				LinterConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);

			for (IMarker marker : markers) {
				if (marker.getAttribute(IMarker.MESSAGE).equals(message) &&
					marker.getAttribute(IMarker.LINE_NUMBER).equals(
						lineNumber)) {

					return;
				}
			}

			final Map<String, Object> attributes = new HashMap<>();

			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.SEVERITY, severity);
			attributes.put(IMarker.CHAR_START, sourceRange.getOffset());
			attributes.put(
				IMarker.CHAR_END,
				sourceRange.getOffset() + sourceRange.getLength());

			if (lineNumber > 0) {
				attributes.put(IMarker.LINE_NUMBER, lineNumber);
			}

			IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					IMarker marker= file.createMarker(
						LinterConstants.MARKER_TYPE);

					marker.setAttributes(attributes);
				}
			};

			file.getWorkspace().run(
				runnable, null, IWorkspace.AVOID_UPDATE, null);
		}
		catch (CoreException e) {
			//ignore
		}
	}

	protected int getLineNumber(IType type, int position)
		throws IOException, JavaModelException {

		String head = type.getCompilationUnit().getSource().substring(
			0, position);

		BufferedReader bufferedReader = new BufferedReader(
			new StringReader(head));

		int lineNumber = 0;

		while (bufferedReader.readLine() != null) {
			lineNumber++;
		}

		return lineNumber;
	}

	private void doType1(IFile file, Diff diff, Info info, String message)
		throws Exception {

		doType2(
			file, diff.getName(), info,
			message + diff.getDelta().toString().toLowerCase() +
				"! Change package version to " + info.suggestedVersion);
	}

	private void doType2(
			IFile file, String typeName, Info info, String message)
		throws Exception {

		IType type = javaProject.findType(typeName);
		ISourceRange nameRange = type.getNameRange();
		int lineNumber = getLineNumber(type, nameRange.getOffset());

		addRangeMarker(
			(IFile)type.getResource(), message, nameRange, lineNumber,
			IMarker.SEVERITY_ERROR);
	}

	private IJavaProject javaProject;

}