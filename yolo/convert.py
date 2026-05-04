from ultralytics import YOLO
import os

def main():
    print("Loading YOLO26s-cls model...")
    # This will download the .pt file to the current directory if it's not found
    model = YOLO('yolo26s-cls.pt')
    
    print("Exporting to ONNX format...")
    # Export the model. 
    # opset=12 is generally well-supported by OpenCV's DNN module.
    # simplify=True requires onnx-simplifier, which might not be installed, 
    # so we'll keep it simple.
    path = model.export(format='onnx', opset=12)
    
    print(f"Model exported to: {path}")

if __name__ == "__main__":
    main()
