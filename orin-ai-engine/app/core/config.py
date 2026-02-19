from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    NODE_DEFAULT_TIMEOUT: float = 60.0 # Seconds
    
    # LLM Settings
    OPENAI_API_KEY: Optional[str] = None
    OPENAI_BASE_URL: Optional[str] = "https://api.openai.com/v1" 
    
    class Config:
        env_prefix = "ORIN_"
        env_file = ".env"
        extra = "ignore" 

settings = Settings()
