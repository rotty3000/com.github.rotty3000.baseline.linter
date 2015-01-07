package com.github.rotty3000.baseline.linter.builder;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

public class SampleNature implements IProjectNature {

	/**
	 * ID of this project nature
	 */
	public static final String NATURE_ID =
		"com.github.rotty3000.baseline.linter.sampleNature";

	@Override
	public void configure() throws CoreException {
		IProjectDescription description = project.getDescription();

		ICommand[] commands = description.getBuildSpec();

		for (ICommand command : commands) {
			if (command.getBuilderName().equals(LinterConstants.BUILDER_ID)) {
				return;
			}
		}

		ICommand command = description.newCommand();
		command.setBuilderName(LinterConstants.BUILDER_ID);

		ICommand[] newCommands = new ICommand[commands.length + 1];
		System.arraycopy(commands, 0, newCommands, 0, commands.length);
		newCommands[newCommands.length - 1] = command;

		description.setBuildSpec(newCommands);

		project.setDescription(description, null);
	}

	@Override
	public void deconfigure() throws CoreException {
		IProjectDescription description = project.getDescription();

		ICommand[] commands = description.getBuildSpec();

		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(LinterConstants.BUILDER_ID)) {
				ICommand[] newCommands = new ICommand[commands.length - 1];

				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(
					commands, i + 1, newCommands, i, commands.length - i - 1);

				description.setBuildSpec(newCommands);

				project.setDescription(description, null);

				return;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.resources.IProjectNature#getProject()
	 */
	@Override
	public IProject getProject() {
		return project;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.resources.IProjectNature#setProject(org.eclipse.core.resources.IProject)
	 */
	@Override
	public void setProject(IProject project) {
		this.project = project;
	}

	private IProject project;

}