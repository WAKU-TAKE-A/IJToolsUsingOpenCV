from ultralytics import YOLO
import os

def convert(model_path):
    # This will download the .pt file to the current directory if it's not found
    model = YOLO(model_path)
    
    print("Exporting to ONNX format...")
    # Export the model. 
    # opset=12 is generally well-supported by OpenCV's DNN module.
    # simplify=True requires onnx-simplifier, which might not be installed, 
    # so we'll keep it simple.
    path = model.export(format='onnx', opset=12)
    
    print(f"Model exported to: {path}")

if __name__ == "__main__":
    convert('yolo11s-pose.pt')
