# Manual for PseudoPFlow Project

---

## Prerequisite

The main coding language for this project is Java, while some data processing and evaluation are conducted with Pandas in Python.

- Java (We use Intellij IDEA as the IDE)
- Python (Pandas, most codes are in the jupyter notebook)
- Git (Codes Control)
- Agent-based simulation

---

## Datasets

### Dataset Overview

- Agent Profiles
- Markov Transition Probability (\markov\\*)
- Telepoints (POIs)
- Road Network

### How to Find These Datasets

- HMS Local Server (/mnt/large/dataset)
- S3

---

## Main content: Development Workflow for PseudoPFlow Project

### Before Revision of the Codes

Please check the following files from Dr. Pang.

- [Readme (General)](https://github.com/sekilab/Pseudo-PFLOW/blob/master/README.md)
- [Branch Policy](https://github.com/sekilab/Pseudo-PFLOW/blob/pseudo-pflow-v3-dev/docs/branch-policy.md)
- [Team Development Workflow](https://github.com/sekilab/Pseudo-PFLOW/blob/pseudo-pflow-v3-dev/docs/team-dev-flow.md)
- [Basic Information for General Human Mobility](https://colab.research.google.com/drive/1GRjTCJve2jEZlFfPGW_xikBkgPNOdR3K#scrollTo=f78afb09-b637-496f-aebb-266e94734f2a)

Please create your own branch to revise or add features. You can check the current branch by:

```
git branch -a
```

**NEVER REVISE CODES ON MASTER OR OTHER PEOPLE'S BRANCH!**

### Revise or Add New Features

We mainly revise the decision choice model in the whole project, thus the revision usually only happens in the following files:

- src/pseudo/ActGenerator.py (parent class of the all agent types)
- src/pseudo/Commuter.py
- src/pseudo/NonCommuter.py
- src/pseudo/Student.py

If the parent class is changed, all children classes that extend it will have revised behaviors. There are main functions as the entry points in the latter three files, which means that you need to generate acticity results for three groups of agents separately.

If you want to revise an existed function, a better way is to define a function with the same name and different parameters, which is called **overloading**. The codes will direct to the correct function from the parameter list.

A Huff model is implemented for the decision choice model ([Introduction of Huff Model](https://pro.arcgis.com/en/pro-app/latest/tool-reference/business-analyst/understanding-huff-model.htm)). The decision choice model is defined separately for commuting behaviors and non-commuting free behaviors. For Huff model, you can change the definition of the attraction or effect of distance to test the performance.

After that, run cells in scripts/Merge_ActivityData.ipynb to merge activities of three types of agents.

Keep your code with a clean style (Reference: [Google Style Guide](https://google.github.io/styleguide/javaguide.html)). Write annotations and function explanations as much as possible.

### Evaluation of Codes

The following manuscripts are used for evaluating the performance of results.

- scripts/SoftbankOD.ipynb
- scripts/SIP_PseudoPFLOW_Eval.ipynb

Generally, we use the following metrics for the evaluation and visualization:

- Trip length
- Link Traffic Volume
- Mesh OD correlation
- City OD correlation

### After Revision of the Codes

- Be sure to commit your change of codes regularly.
- Keep your commit message clear and understandable.
- Log your revised or new features in CHANGELOG.md with the correct format.
- Save the simulated files and upload them to S3 ([s3://pseudo-pflow/ver2.0/dev/](s3://pseudo-pflow/ver2.0/dev/)) with the name of the experiment.

---

## References

### Related Papers

You can check the following papers for generation of Pseudo PFLOW project.

- [Nationwide synthetic human mobility dataset construction from limited travel surveys and open data](https://onlinelibrary.wiley.com/doi/abs/10.1111/mice.13285)
- [Development of current estimated household data and agent-based simulation of the future population distribution of households in Japan](https://scholar.google.com/citations?view_op=view_citation&hl=zh-CN&user=8aWZwJ0AAAAJ&citation_for_view=8aWZwJ0AAAAJ:qjMakFHDy7sC)

### Related Websites

Introduction of the pseudo PFLOW project can be found at:

- [PFLOW Project](https://pflow.csis.u-tokyo.ac.jp)
