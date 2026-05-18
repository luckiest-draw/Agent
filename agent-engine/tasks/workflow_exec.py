# Workflow Execution Task: 异步执行工作流
from tasks.celery_app import celery_app
from workflows.workflow_executor import execute_workflow


@celery_app.task(bind=True, name="workflow.exec")
def execute_workflow_task(self, workflow_def: dict, model: str = None):
    """异步执行工作流"""
    try:
        result = execute_workflow(workflow_def, model or "deepseek-chat")
        return {"status": "done", "result": result}
    except Exception as e:
        return {"status": "failed", "error": str(e)}
