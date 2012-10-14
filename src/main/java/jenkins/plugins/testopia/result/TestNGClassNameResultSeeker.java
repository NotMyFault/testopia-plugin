/*
 * The MIT License
 *
 * Copyright (c) <2011> <Bruno P. Kinoshita>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugins.testopia.result;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.testopia.TestopiaSite;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.mozilla.testopia.model.Status;

import com.tupilabs.testng.parser.Suite;
import com.tupilabs.testng.parser.Test;
import com.tupilabs.testng.parser.TestMethod;

/**
 * <p>Seeks for test results matching each TestNG Class name with the key 
 * custom field.</p>
 * 
 * <p>Skips TestNG Suite that were disabled.</p>
 * 
 * @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
 * @since 1.2
 */
public class TestNGClassNameResultSeeker extends AbstractTestNGResultSeeker {
	
	private static final long serialVersionUID = 6349875034415550272L;

	/**
	 * @param includePattern
	 * @param attachTestNGXML
	 * @param markSkippedTestAsBlocked
	 */
	@DataBoundConstructor
	public TestNGClassNameResultSeeker(String includePattern, boolean attachTestNGXML, boolean markSkippedTestAsBlocked) {
		super(includePattern, attachTestNGXML, markSkippedTestAsBlocked);
	}
	
	@Extension
	public static class DescriptorImpl extends ResultSeekerDescriptor {
		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return "TestNG class name"; // TBD: i18n
		}
	}

	@Override
	public void seek(TestCaseWrapper[] automatedTestCases, AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener, TestopiaSite testopia) throws ResultSeekerException {
		listener.getLogger().println( "Looking for test results using TestNG class name" ); // TBD: i18n
		try {
			final List<Suite> suites = build.getWorkspace().act(new FilePath.FileCallable<List<Suite>>() {
				private static final long serialVersionUID = 1L;

				private List<Suite> suites = new ArrayList<Suite>();
				
				public List<Suite> invoke(File workspace, VirtualChannel channel)
						throws IOException, InterruptedException {
					final String[] xmls = TestNGClassNameResultSeeker.this.scan(workspace, includePattern, listener);
					
					for(String xml : xmls) {
						final File input = new File(workspace, xml);
						Suite suite = parser.parse(input);
						suites.add(suite);
					}
					
					return suites;
				}
			});
			for(Suite suite : suites) {
				for(Test test : suite.getTests() ) {
					for(com.tupilabs.testng.parser.Class  clazz : test.getClasses()) {
						for(TestCaseWrapper automatedTestCase : automatedTestCases) {
							if(clazz.getName().equals(automatedTestCase.getAlias())) {
								Status status = this.getExecutionStatus(clazz);
								automatedTestCase.setStatusId(status.getValue());
								testopia.updateTestCase(automatedTestCase);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			throw new ResultSeekerException(e);
		} catch (InterruptedException e) {
			throw new ResultSeekerException(e);
		} 
	}

	/**
	 * By default this method returns Passed. 
	 * 
	 * <p>
	 * If there is any failed method, then it returns Failed.
	 * 
	 * <p>
	 * In there is any skipped method, and {{@link #isMarkSkippedTestAsBlocked()} 
	 * is true, then it returns Blocked.
	 * 
	 * @param suite
	 * @return
	 */
	private Status getExecutionStatus(com.tupilabs.testng.parser.Class clazz) {
		for( TestMethod method : clazz.getTestMethods() ) {
			if ( StringUtils.isNotBlank(method.getStatus()) ) {
				if(method.getStatus().equals(FAIL)) {
					return Status.FAILED; 
				} else if(method.getStatus().equals(SKIP)) {
					if(this.isMarkSkippedTestAsBlocked()) { 
						return Status.BLOCKED;
					} else {
						return Status.IDLE;
					}
				}
			}
		}
		return Status.PASSED;
	}

}
